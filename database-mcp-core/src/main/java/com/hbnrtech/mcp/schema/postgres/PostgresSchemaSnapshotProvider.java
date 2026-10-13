package com.hbnrtech.mcp.schema.postgres;

import com.hbnrtech.mcp.schema.ColumnDef;
import com.hbnrtech.mcp.schema.ConstraintDef;
import com.hbnrtech.mcp.schema.IndexDef;
import com.hbnrtech.mcp.schema.RoutineDef;
import com.hbnrtech.mcp.schema.SchemaSnapshot;
import com.hbnrtech.mcp.schema.SchemaSnapshotProvider;
import com.hbnrtech.mcp.schema.SequenceDef;
import com.hbnrtech.mcp.schema.TableDef;
import com.hbnrtech.mcp.schema.ViewDef;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PostgresSchemaSnapshotProvider implements SchemaSnapshotProvider {
   @Override
   public SchemaSnapshot loadSnapshot(Connection connection, String schema) throws SQLException {
      String resolvedSchema = this.resolveSchema(connection, schema);
      return new SchemaSnapshot(
         resolvedSchema,
         this.getTables(connection, resolvedSchema),
         this.getIndexes(connection, resolvedSchema),
         this.getConstraints(connection, resolvedSchema),
         this.getViews(connection, resolvedSchema),
         this.getRoutines(connection, resolvedSchema),
         this.getSequences(connection, resolvedSchema)
      );
   }

   @Override
   public String buildTableDdl(Connection connection, String schema, String tableName) throws SQLException {
      String resolvedSchema = this.resolveSchema(connection, schema);
      return this.renderTableDdl(
         resolvedSchema,
         tableName,
         this.loadColumns(connection, resolvedSchema, tableName).getOrDefault(tableName, new LinkedHashMap<>()),
         this.loadTableComments(connection, resolvedSchema, tableName).get(tableName),
         this.loadColumnComments(connection, resolvedSchema, tableName).getOrDefault(tableName, new LinkedHashMap<>())
      );
   }

   /**
    * Renders a table DDL from already fetched metadata. Kept free of JDBC so the rendering can be
    * unit tested, and so {@code getTables} can render every table from bulk queries.
    */
   String renderTableDdl(String schema, String tableName, Map<String, ColumnDef> columns, String tableComment, Map<String, String> columnComments) {
      StringBuilder sb = new StringBuilder();
      sb.append("CREATE TABLE ").append(this.quoteIdentifier(schema)).append(".").append(this.quoteIdentifier(tableName)).append(" (\n");
      List<String> colDefs = new ArrayList<>();

      for (ColumnDef col : columns.values()) {
         StringBuilder colDef = new StringBuilder();
         colDef.append("    ").append(this.quoteIdentifier(col.name())).append(" ").append(col.dataType());
         if (!col.nullable()) {
            colDef.append(" NOT NULL");
         }
         if (col.defaultValue() != null) {
            colDef.append(" DEFAULT ").append(col.defaultValue());
         }
         colDefs.add(colDef.toString());
      }

      sb.append(String.join(",\n", colDefs));
      sb.append("\n)");
      this.appendCommentStatements(schema, tableName, tableComment, columnComments, sb);
      return sb.toString();
   }

   private void appendCommentStatements(String schema, String tableName, String tableComment, Map<String, String> columnComments, StringBuilder sb) {
      if ((tableComment == null || tableComment.isBlank()) && columnComments.isEmpty()) {
         return;
      }

      sb.append(";");
      if (tableComment != null && !tableComment.isBlank()) {
         sb.append("\n\nCOMMENT ON TABLE ")
            .append(this.qualifiedTableName(schema, tableName))
            .append(" IS ")
            .append(this.quoteLiteral(tableComment))
            .append(";");
      }
      for (Map.Entry<String, String> entry : columnComments.entrySet()) {
         String comment = entry.getValue();
         if (comment != null && !comment.isBlank()) {
            sb.append("\nCOMMENT ON COLUMN ")
               .append(this.qualifiedTableName(schema, tableName))
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
   private Map<String, TableDef> getTables(Connection connection, String schema) throws SQLException {
      List<String> tableNames = this.loadTableNames(connection, schema);
      Map<String, Map<String, ColumnDef>> columnsByTable = this.loadColumns(connection, schema, null);
      Map<String, String> tableComments = this.loadTableComments(connection, schema, null);
      Map<String, Map<String, String>> columnCommentsByTable = this.loadColumnComments(connection, schema, null);

      Map<String, TableDef> tables = new LinkedHashMap<>();
      for (String tableName : tableNames) {
         Map<String, ColumnDef> columns = columnsByTable.getOrDefault(tableName, new LinkedHashMap<>());
         String ddl = this.renderTableDdl(schema, tableName, columns, tableComments.get(tableName), columnCommentsByTable.getOrDefault(tableName, new LinkedHashMap<>()));
         tables.put(tableName, new TableDef(tableName, schema, ddl, columns));
      }
      return tables;
   }

   private List<String> loadTableNames(Connection connection, String schema) throws SQLException {
      List<String> tableNames = new ArrayList<>();
      String sql = "SELECT t.table_name FROM information_schema.tables t WHERE t.table_schema = ? AND t.table_type = 'BASE TABLE' ORDER BY t.table_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
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
   private Map<String, Map<String, ColumnDef>> loadColumns(Connection connection, String schema, String tableName) throws SQLException {
      Map<String, Map<String, ColumnDef>> columnsByTable = new LinkedHashMap<>();
      String sql = "SELECT c.table_name, c.column_name, c.data_type, c.character_maximum_length, c.numeric_precision, c.numeric_scale, c.is_nullable, c.column_default, c.ordinal_position "
         + "FROM information_schema.columns c WHERE c.table_schema = ?"
         + (tableName != null ? " AND c.table_name = ?" : "")
         + " ORDER BY c.table_name, c.ordinal_position";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         if (tableName != null) {
            stmt.setString(2, tableName);
         }
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String name = rs.getString("column_name");
               columnsByTable
                  .computeIfAbsent(rs.getString("table_name"), key -> new LinkedHashMap<>())
                  .put(name, new ColumnDef(name, buildDataType(rs), "YES".equals(rs.getString("is_nullable")), rs.getString("column_default"), rs.getInt("ordinal_position")));
            }
         }
      }
      return columnsByTable;
   }

   private Map<String, String> loadTableComments(Connection connection, String schema, String tableName) throws SQLException {
      Map<String, String> comments = new LinkedHashMap<>();
      String sql = "SELECT c.relname AS table_name, obj_description(c.oid, 'pg_class') AS table_comment "
         + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
         + "WHERE n.nspname = ? AND c.relkind IN ('r', 'p')"
         + (tableName != null ? " AND c.relname = ?" : "");
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         if (tableName != null) {
            stmt.setString(2, tableName);
         }
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               comments.put(rs.getString("table_name"), rs.getString("table_comment"));
            }
         }
      }
      return comments;
   }

   private Map<String, Map<String, String>> loadColumnComments(Connection connection, String schema, String tableName) throws SQLException {
      Map<String, Map<String, String>> commentsByTable = new LinkedHashMap<>();
      String sql = "SELECT c.relname AS table_name, a.attname, col_description(c.oid, a.attnum) AS column_comment "
         + "FROM pg_attribute a "
         + "JOIN pg_class c ON c.oid = a.attrelid "
         + "JOIN pg_namespace n ON n.oid = c.relnamespace "
         + "WHERE n.nspname = ? AND a.attnum > 0 AND NOT a.attisdropped"
         + (tableName != null ? " AND c.relname = ?" : "")
         + " ORDER BY c.relname, a.attnum";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         if (tableName != null) {
            stmt.setString(2, tableName);
         }
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String comment = rs.getString("column_comment");
               if (comment != null && !comment.isBlank()) {
                  commentsByTable
                     .computeIfAbsent(rs.getString("table_name"), key -> new LinkedHashMap<>())
                     .put(rs.getString("attname"), comment);
               }
            }
         }
      }
      return commentsByTable;
   }

   private Map<String, IndexDef> getIndexes(Connection connection, String schema) throws SQLException {
      Map<String, IndexDef> indexes = new LinkedHashMap<>();
      String sql = "SELECT indexname, tablename, indexdef FROM pg_indexes WHERE schemaname = ? ORDER BY indexname";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               indexes.put(rs.getString("indexname"), new IndexDef(rs.getString("indexname"), rs.getString("tablename"), rs.getString("indexdef")));
            }
         }
      }
      return indexes;
   }

   private Map<String, ConstraintDef> getConstraints(Connection connection, String schema) throws SQLException {
      Map<String, ConstraintDef> constraints = new LinkedHashMap<>();
      String sql = "SELECT tc.constraint_name, tc.table_name, tc.constraint_type, "
         + "CASE tc.constraint_type "
         + "WHEN 'PRIMARY KEY' THEN (SELECT 'PRIMARY KEY (' || string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) || ')' "
         + "FROM information_schema.key_column_usage kcu WHERE kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema) "
         + "WHEN 'UNIQUE' THEN (SELECT 'UNIQUE (' || string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) || ')' "
         + "FROM information_schema.key_column_usage kcu WHERE kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema) "
         + "WHEN 'FOREIGN KEY' THEN (SELECT 'FOREIGN KEY (' || string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) || ') REFERENCES ' || "
         + "ccu.table_name || ' (' || ccu.column_name || ')' "
         + "FROM information_schema.key_column_usage kcu "
         + "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name "
         + "WHERE kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema) "
         + "ELSE '' END AS definition "
         + "FROM information_schema.table_constraints tc "
         + "WHERE tc.table_schema = ? AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE', 'FOREIGN KEY') ORDER BY tc.constraint_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               constraints.put(
                  rs.getString("constraint_name"),
                  new ConstraintDef(rs.getString("constraint_name"), rs.getString("table_name"), rs.getString("constraint_type"), rs.getString("definition"))
               );
            }
         }
      }
      return constraints;
   }

   private Map<String, ViewDef> getViews(Connection connection, String schema) throws SQLException {
      Map<String, ViewDef> views = new LinkedHashMap<>();
      String sql = "SELECT viewname, definition FROM pg_views WHERE schemaname = ? ORDER BY viewname";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               views.put(rs.getString("viewname"), new ViewDef(rs.getString("viewname"), rs.getString("definition")));
            }
         }
      }
      return views;
   }

   private Map<String, RoutineDef> getRoutines(Connection connection, String schema) throws SQLException {
      Map<String, RoutineDef> routines = new LinkedHashMap<>();
      String sql = "SELECT p.proname AS func_name, pg_get_function_identity_arguments(p.oid) AS func_args "
         + "FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid "
         + "WHERE n.nspname = ? AND p.prokind = 'f' ORDER BY p.proname";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String signature = rs.getString("func_name") + "(" + (rs.getString("func_args") != null ? rs.getString("func_args") : "") + ")";
               routines.put(signature, new RoutineDef(signature, "FUNCTION " + signature));
            }
         }
      }
      return routines;
   }

   private Map<String, SequenceDef> getSequences(Connection connection, String schema) throws SQLException {
      Map<String, SequenceDef> sequences = new LinkedHashMap<>();
      String sql = "SELECT sequence_name, 'CREATE SEQUENCE ' || sequence_name || "
         + "' START WITH ' || start_value || "
         + "' INCREMENT BY ' || increment_by || "
         + "' MINVALUE ' || min_value || "
         + "' MAXVALUE ' || max_value || "
         + "CASE WHEN cycle = 'YES' THEN ' CYCLE' ELSE ' NO CYCLE' END AS definition "
         + "FROM information_schema.sequences WHERE sequence_schema = ? ORDER BY sequence_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
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
      Integer charMaxLen = rs.getObject("character_maximum_length", Integer.class);
      Integer numPrecision = rs.getObject("numeric_precision", Integer.class);
      Integer numScale = rs.getObject("numeric_scale", Integer.class);
      if (charMaxLen != null) {
         return type + "(" + charMaxLen + ")";
      }
      if (numPrecision != null && numScale != null && numScale > 0) {
         return type + "(" + numPrecision + "," + numScale + ")";
      }
      return numPrecision != null ? type + "(" + numPrecision + ")" : type;
   }

   private String quoteIdentifier(String identifier) {
      return "\"" + identifier.replace("\"", "\"\"") + "\"";
   }

   private String qualifiedTableName(String schema, String tableName) {
      return this.quoteIdentifier(schema) + "." + this.quoteIdentifier(tableName);
   }

   private String quoteLiteral(String value) {
      return "'" + value.replace("'", "''") + "'";
   }

   private String resolveSchema(Connection connection, String schema) throws SQLException {
      if (schema != null && !schema.isBlank()) {
         return schema;
      }
      String currentSchema = connection.getSchema();
      if (currentSchema != null && !currentSchema.isBlank()) {
         return currentSchema;
      }
      throw new SQLException("Unable to determine PostgreSQL current schema");
   }
}
