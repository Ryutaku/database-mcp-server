package com.hbnrtech.mcp.schema.oracle;

import com.hbnrtech.mcp.schema.ColumnDef;
import com.hbnrtech.mcp.schema.ConstraintDef;
import com.hbnrtech.mcp.schema.IndexDef;
import com.hbnrtech.mcp.schema.RoutineDef;
import com.hbnrtech.mcp.schema.SchemaSnapshot;
import com.hbnrtech.mcp.schema.SchemaSnapshotProvider;
import com.hbnrtech.mcp.schema.SequenceDef;
import com.hbnrtech.mcp.schema.TableDef;
import com.hbnrtech.mcp.schema.ViewDef;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OracleSchemaSnapshotProvider implements SchemaSnapshotProvider {
   /** Guards against a single oversized metadata object (view text) exhausting memory. */
   static final int MAX_METADATA_TEXT_CHARS = 1_000_000;

   @Override
   public SchemaSnapshot loadSnapshot(Connection connection, String schema) throws SQLException {
      String owner = normalizeOwner(schema, connection);
      return new SchemaSnapshot(
         owner,
         this.getTables(connection, owner),
         this.getIndexes(connection, owner),
         this.getConstraints(connection, owner),
         this.getViews(connection, owner),
         this.getRoutines(connection, owner),
         this.getSequences(connection, owner)
      );
   }

   @Override
   public String buildTableDdl(Connection connection, String schema, String tableName) throws SQLException {
      String owner = normalizeOwner(schema, connection);
      return this.renderTableDdl(
         owner,
         tableName,
         this.loadColumns(connection, owner, tableName).getOrDefault(tableName, new LinkedHashMap<>()),
         this.loadTableComments(connection, owner, tableName).get(tableName),
         this.loadColumnComments(connection, owner, tableName).getOrDefault(tableName, new LinkedHashMap<>())
      );
   }

   /**
    * Renders a table DDL from already fetched metadata. Kept free of JDBC so the rendering can be
    * unit tested, and so {@code getTables} can render every table from bulk queries.
    */
   String renderTableDdl(String owner, String tableName, Map<String, ColumnDef> columns, String tableComment, Map<String, String> columnComments) {
      StringBuilder sb = new StringBuilder();
      sb.append("CREATE TABLE ").append(this.quoteIdentifier(owner)).append(".").append(this.quoteIdentifier(tableName)).append(" (\n");
      List<String> columnDefs = new ArrayList<>();

      for (ColumnDef col : columns.values()) {
         StringBuilder colDef = new StringBuilder();
         colDef.append("    ").append(this.quoteIdentifier(col.name())).append(" ").append(col.dataType());
         if (!col.nullable()) {
            colDef.append(" NOT NULL");
         }
         if (col.defaultValue() != null && !col.defaultValue().isBlank()) {
            colDef.append(" DEFAULT ").append(col.defaultValue().trim());
         }
         columnDefs.add(colDef.toString());
      }

      sb.append(String.join(",\n", columnDefs));
      sb.append("\n)");
      this.appendCommentStatements(owner, tableName, tableComment, columnComments, sb);
      return sb.toString();
   }

   private void appendCommentStatements(String owner, String tableName, String tableComment, Map<String, String> columnComments, StringBuilder sb) {
      if ((tableComment == null || tableComment.isBlank()) && columnComments.isEmpty()) {
         return;
      }

      sb.append(";");
      if (tableComment != null && !tableComment.isBlank()) {
         sb.append("\n\nCOMMENT ON TABLE ")
            .append(this.qualifiedTableName(owner, tableName))
            .append(" IS ")
            .append(this.quoteLiteral(tableComment))
            .append(";");
      }
      for (Map.Entry<String, String> entry : columnComments.entrySet()) {
         String comment = entry.getValue();
         if (comment != null && !comment.isBlank()) {
            sb.append("\nCOMMENT ON COLUMN ")
               .append(this.qualifiedTableName(owner, tableName))
               .append(".")
               .append(this.quoteIdentifier(entry.getKey()))
               .append(" IS ")
               .append(this.quoteLiteral(comment))
               .append(";");
         }
      }
   }

   /**
    * Bulk fetch: one query for the table list, one for all columns, one for table comments and one
    * for column comments. The previous per-table rendering cost five round trips per table.
    */
   private Map<String, TableDef> getTables(Connection connection, String owner) throws SQLException {
      List<String> tableNames = this.loadTableNames(connection, owner);
      Map<String, Map<String, ColumnDef>> columnsByTable = this.loadColumns(connection, owner, null);
      Map<String, String> tableComments = this.loadTableComments(connection, owner, null);
      Map<String, Map<String, String>> columnCommentsByTable = this.loadColumnComments(connection, owner, null);

      Map<String, TableDef> tables = new LinkedHashMap<>();
      for (String tableName : tableNames) {
         Map<String, ColumnDef> columns = columnsByTable.getOrDefault(tableName, new LinkedHashMap<>());
         String ddl = this.renderTableDdl(owner, tableName, columns, tableComments.get(tableName), columnCommentsByTable.getOrDefault(tableName, new LinkedHashMap<>()));
         tables.put(tableName, new TableDef(tableName, owner, ddl, columns));
      }
      return tables;
   }

   private List<String> loadTableNames(Connection connection, String owner) throws SQLException {
      List<String> tableNames = new ArrayList<>();
      String sql = "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               tableNames.add(rs.getString("table_name"));
            }
         }
      }
      return tableNames;
   }

   /**
    * @param tableName when non-null, restricts the query to a single table
    * @return columns keyed by table name, then by column name in ordinal order
    */
   private Map<String, Map<String, ColumnDef>> loadColumns(Connection connection, String owner, String tableName) throws SQLException {
      Map<String, Map<String, ColumnDef>> columnsByTable = new LinkedHashMap<>();
      String sql = "SELECT table_name, column_name, data_type, data_length, data_precision, data_scale, nullable, data_default, column_id "
         + "FROM all_tab_columns WHERE owner = ?"
         + (tableName != null ? " AND table_name = ?" : "")
         + " ORDER BY table_name, column_id";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         if (tableName != null) {
            stmt.setString(2, tableName);
         }
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String name = rs.getString("column_name");
               columnsByTable
                  .computeIfAbsent(rs.getString("table_name"), key -> new LinkedHashMap<>())
                  .put(name, new ColumnDef(name, buildDataType(rs), "Y".equals(rs.getString("nullable")), rs.getString("data_default"), rs.getInt("column_id")));
            }
         }
      }
      return columnsByTable;
   }

   private Map<String, String> loadTableComments(Connection connection, String owner, String tableName) throws SQLException {
      Map<String, String> comments = new LinkedHashMap<>();
      String sql = "SELECT table_name, comments FROM all_tab_comments WHERE owner = ? AND table_type = 'TABLE'"
         + (tableName != null ? " AND table_name = ?" : "");
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         if (tableName != null) {
            stmt.setString(2, tableName);
         }
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               comments.put(rs.getString("table_name"), rs.getString("comments"));
            }
         }
      }
      return comments;
   }

   private Map<String, Map<String, String>> loadColumnComments(Connection connection, String owner, String tableName) throws SQLException {
      Map<String, Map<String, String>> commentsByTable = new LinkedHashMap<>();
      String sql = "SELECT table_name, column_name, comments FROM all_col_comments WHERE owner = ?"
         + (tableName != null ? " AND table_name = ?" : "")
         + " ORDER BY table_name, column_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         if (tableName != null) {
            stmt.setString(2, tableName);
         }
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String comment = rs.getString("comments");
               if (comment != null && !comment.isBlank()) {
                  commentsByTable
                     .computeIfAbsent(rs.getString("table_name"), key -> new LinkedHashMap<>())
                     .put(rs.getString("column_name"), comment);
               }
            }
         }
      }
      return commentsByTable;
   }

   /**
    * Bulk fetch of index columns, aggregated in Java. LISTAGG was dropped because its result is a
    * VARCHAR2 and a wide composite key raises ORA-01489.
    */
   private Map<String, IndexDef> getIndexes(Connection connection, String owner) throws SQLException {
      Map<String, IndexDef> indexes = new LinkedHashMap<>();
      String sql = "SELECT i.index_name, i.table_name, i.table_owner, "
         + "CASE WHEN i.uniqueness = 'UNIQUE' THEN 1 ELSE 0 END AS is_unique, "
         + "c.column_name "
         + "FROM all_indexes i "
         + "JOIN all_ind_columns c ON c.index_owner = i.owner AND c.index_name = i.index_name "
         + "WHERE i.owner = ? AND i.generated = 'N' "
         + "ORDER BY i.index_name, c.column_position";
      List<String> columnNames = new ArrayList<>();
      String currentIndex = null;
      String indexOwner = null;
      String tableOwner = null;
      String tableName = null;
      boolean unique = false;

      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String indexName = rs.getString("index_name");
               if (!indexName.equals(currentIndex)) {
                  if (currentIndex != null) {
                     indexes.put(currentIndex, new IndexDef(currentIndex, tableName, renderIndexDefinition(indexOwner, currentIndex, tableOwner, tableName, unique, columnNames)));
                  }
                  currentIndex = indexName;
                  indexOwner = owner;
                  tableOwner = rs.getString("table_owner");
                  tableName = rs.getString("table_name");
                  unique = rs.getInt("is_unique") == 1;
                  columnNames = new ArrayList<>();
               }
               columnNames.add(rs.getString("column_name"));
            }
         }
      }

      if (currentIndex != null) {
         indexes.put(currentIndex, new IndexDef(currentIndex, tableName, renderIndexDefinition(indexOwner, currentIndex, tableOwner, tableName, unique, columnNames)));
      }
      return indexes;
   }

   static String renderIndexDefinition(String indexOwner, String indexName, String tableOwner, String tableName, boolean unique, List<String> columns) {
      return (unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
         + indexOwner + "." + indexName
         + " ON " + tableOwner + "." + tableName
         + " (" + String.join(", ", columns) + ")";
   }

   private Map<String, ConstraintDef> getConstraints(Connection connection, String owner) throws SQLException {
      List<ConstraintRow> rows = this.loadConstraintRows(connection, owner);
      Map<String, List<String>> localColumns = this.loadConstraintColumns(connection, owner, null);

      Set<String> referencedOwners = new LinkedHashSet<>();
      for (ConstraintRow row : rows) {
         if ("R".equals(row.constraintType()) && row.refOwner() != null && !row.refOwner().equals(owner)) {
            referencedOwners.add(row.refOwner());
         }
      }

      Map<String, Map<String, List<String>>> referencedColumns = new LinkedHashMap<>();
      for (String referencedOwner : referencedOwners) {
         Set<String> constraintNames = new LinkedHashSet<>();
         for (ConstraintRow row : rows) {
            if (referencedOwner.equals(row.refOwner())) {
               constraintNames.add(row.refConstraintName());
            }
         }
         referencedColumns.put(referencedOwner, this.loadConstraintColumns(connection, referencedOwner, constraintNames));
      }

      Map<String, ConstraintDef> constraints = new LinkedHashMap<>();
      for (ConstraintRow row : rows) {
         List<String> columns = localColumns.get(row.name());
         if (columns == null || columns.isEmpty()) {
            continue;
         }

         List<String> refColumns = null;
         if ("R".equals(row.constraintType()) && row.refOwner() != null) {
            Map<String, List<String>> byName = owner.equals(row.refOwner()) ? localColumns : referencedColumns.get(row.refOwner());
            refColumns = byName == null ? null : byName.get(row.refConstraintName());
         }

         String definition = renderConstraintDefinition(row.constraintType(), columns, row.refOwner(), row.refTableName(), refColumns);
         constraints.put(row.name(), new ConstraintDef(row.name(), row.tableName(), constraintTypeName(row.constraintType()), definition));
      }
      return constraints;
   }

   private List<ConstraintRow> loadConstraintRows(Connection connection, String owner) throws SQLException {
      List<ConstraintRow> rows = new ArrayList<>();
      String sql = "SELECT c.constraint_name, c.table_name, c.constraint_type, c.r_owner, c.r_constraint_name, rc.table_name AS ref_table_name "
         + "FROM all_constraints c "
         + "LEFT JOIN all_constraints rc ON rc.owner = c.r_owner AND rc.constraint_name = c.r_constraint_name "
         + "WHERE c.owner = ? AND c.constraint_type IN ('P', 'U', 'R') "
         + "ORDER BY c.constraint_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               rows.add(new ConstraintRow(
                  rs.getString("constraint_name"),
                  rs.getString("table_name"),
                  rs.getString("constraint_type"),
                  rs.getString("r_owner"),
                  rs.getString("r_constraint_name"),
                  rs.getString("ref_table_name")
               ));
            }
         }
      }
      return rows;
   }

   /**
    * @param constraintNames when null, every constraint of the owner is fetched; otherwise the list
    *                        is chunked to stay below the Oracle IN-list limit
    */
   private Map<String, List<String>> loadConstraintColumns(Connection connection, String owner, Collection<String> constraintNames) throws SQLException {
      Map<String, List<String>> columnsByName = new LinkedHashMap<>();
      if (constraintNames != null && constraintNames.isEmpty()) {
         return columnsByName;
      }

      List<String> names = constraintNames == null ? List.of() : new ArrayList<>(constraintNames);
      int chunkSize = 900;

      for (int from = 0; constraintNames == null || from < names.size(); from += chunkSize) {
         List<String> chunk = constraintNames == null ? List.of() : names.subList(from, Math.min(from + chunkSize, names.size()));
         String sql = "SELECT constraint_name, column_name FROM all_cons_columns WHERE owner = ?"
            + (constraintNames == null ? "" : " AND constraint_name IN (" + placeholders(chunk.size()) + ")")
            + " ORDER BY constraint_name, position";
         try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, owner);
            for (int i = 0; i < chunk.size(); i++) {
               stmt.setString(i + 2, chunk.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
               while (rs.next()) {
                  columnsByName.computeIfAbsent(rs.getString("constraint_name"), key -> new ArrayList<>()).add(rs.getString("column_name"));
               }
            }
         }
      }
      return columnsByName;
   }

   static String renderConstraintDefinition(String constraintType, List<String> columns, String refOwner, String refTableName, List<String> refColumns) {
      String columnList = String.join(", ", columns);
      return switch (constraintType) {
         case "P" -> "PRIMARY KEY (" + columnList + ")";
         case "U" -> "UNIQUE (" + columnList + ")";
         case "R" -> "FOREIGN KEY (" + columnList + ") REFERENCES "
            + refOwner + "." + (refTableName == null ? "" : refTableName)
            + " (" + String.join(", ", refColumns == null ? List.of() : refColumns) + ")";
         default -> "";
      };
   }

   private static String constraintTypeName(String constraintType) {
      return switch (constraintType) {
         case "P" -> "PRIMARY KEY";
         case "U" -> "UNIQUE";
         case "R" -> "FOREIGN KEY";
         default -> constraintType;
      };
   }

   private static String placeholders(int count) {
      return String.join(", ", java.util.Collections.nCopies(count, "?"));
   }

   private Map<String, ViewDef> getViews(Connection connection, String owner) throws SQLException {
      Map<String, ViewDef> views = new LinkedHashMap<>();
      String sql = "SELECT view_name, text FROM all_views WHERE owner = ? ORDER BY view_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String viewName = rs.getString("view_name");
               views.put(viewName, new ViewDef(viewName, readLongText(rs, "text")));
            }
         }
      }
      return views;
   }

   /**
    * {@code all_views.text} is a LONG column. {@code getString} returns only what the driver fetched
    * by default, which silently truncates long view definitions and hides real differences.
    */
   static String readLongText(ResultSet rs, String column) throws SQLException {
      Reader reader = rs.getCharacterStream(column);
      if (reader == null) {
         return rs.getString(column);
      }

      try (reader) {
         StringBuilder text = new StringBuilder();
         char[] buffer = new char[8192];
         int read;
         while ((read = reader.read(buffer)) != -1) {
            if (text.length() + read > MAX_METADATA_TEXT_CHARS) {
               text.append(buffer, 0, Math.max(0, MAX_METADATA_TEXT_CHARS - text.length()));
               return text.toString();
            }
            text.append(buffer, 0, read);
         }
         return text.toString();
      } catch (IOException ex) {
         throw new SQLException("Failed to read " + column + ": " + ex.getMessage(), ex);
      }
   }

   private Map<String, RoutineDef> getRoutines(Connection connection, String owner) throws SQLException {
      Map<String, RoutineDef> routines = new LinkedHashMap<>();
      String sql = "SELECT object_name, object_type FROM all_objects "
         + "WHERE owner = ? AND object_type IN ('FUNCTION', 'PROCEDURE') ORDER BY object_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String objectName = rs.getString("object_name");
               String objectType = rs.getString("object_type");
               routines.put(objectName, new RoutineDef(objectName, objectType + " " + objectName));
            }
         }
      }
      return routines;
   }

   private Map<String, SequenceDef> getSequences(Connection connection, String owner) throws SQLException {
      Map<String, SequenceDef> sequences = new LinkedHashMap<>();
      String sql = "SELECT sequence_name, "
         + "'CREATE SEQUENCE ' || sequence_owner || '.' || sequence_name || "
         + "' MINVALUE ' || min_value || "
         + "' MAXVALUE ' || max_value || "
         + "' INCREMENT BY ' || increment_by || "
         + "' START WITH ' || min_value || "
         + "CASE WHEN cycle_flag = 'Y' THEN ' CYCLE' ELSE ' NOCYCLE' END AS definition "
         + "FROM all_sequences WHERE sequence_owner = ? ORDER BY sequence_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               sequences.put(rs.getString("sequence_name"), new SequenceDef(rs.getString("sequence_name"), rs.getString("definition")));
            }
         }
      }
      return sequences;
   }

   private static String buildDataType(ResultSet rs) throws SQLException {
      String type = rs.getString("data_type");
      Integer length = rs.getObject("data_length", Integer.class);
      Integer precision = rs.getObject("data_precision", Integer.class);
      Integer scale = rs.getObject("data_scale", Integer.class);

      return switch (type) {
         case "CHAR", "NCHAR", "VARCHAR2", "NVARCHAR2", "RAW" -> length != null ? type + "(" + length + ")" : type;
         case "NUMBER" -> {
            if (precision != null && scale != null) {
               yield scale > 0 ? type + "(" + precision + "," + scale + ")" : type + "(" + precision + ")";
            }
            yield type;
         }
         default -> type;
      };
   }

   private static String normalizeOwner(String schema, Connection connection) throws SQLException {
      if (schema != null && !schema.isBlank()) {
         return schema.trim().toUpperCase();
      }

      try (PreparedStatement stmt = connection.prepareStatement("SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS current_schema FROM dual");
           ResultSet rs = stmt.executeQuery()) {
         if (rs.next()) {
            return rs.getString("current_schema");
         }
      }

      throw new SQLException("Unable to determine Oracle schema owner");
   }

   private String quoteIdentifier(String identifier) {
      return "\"" + identifier.replace("\"", "\"\"") + "\"";
   }

   private String qualifiedTableName(String owner, String tableName) {
      return this.quoteIdentifier(owner) + "." + this.quoteIdentifier(tableName);
   }

   private String quoteLiteral(String value) {
      return "'" + value.replace("'", "''") + "'";
   }

   private record ConstraintRow(
      String name,
      String tableName,
      String constraintType,
      String refOwner,
      String refConstraintName,
      String refTableName
   ) {
   }
}
