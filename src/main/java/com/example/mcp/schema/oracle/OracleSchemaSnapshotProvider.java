package com.example.mcp.schema.oracle;

import com.example.mcp.schema.ColumnDef;
import com.example.mcp.schema.ConstraintDef;
import com.example.mcp.schema.IndexDef;
import com.example.mcp.schema.RoutineDef;
import com.example.mcp.schema.SchemaSnapshot;
import com.example.mcp.schema.SchemaSnapshotProvider;
import com.example.mcp.schema.SequenceDef;
import com.example.mcp.schema.TableDef;
import com.example.mcp.schema.ViewDef;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OracleSchemaSnapshotProvider implements SchemaSnapshotProvider {
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
      StringBuilder sb = new StringBuilder();
      sb.append("CREATE TABLE ").append(this.quoteIdentifier(owner)).append(".").append(this.quoteIdentifier(tableName)).append(" (\n");
      Map<String, ColumnDef> columns = this.getColumns(connection, owner, tableName);
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
      return sb.toString();
   }

   private Map<String, TableDef> getTables(Connection connection, String owner) throws SQLException {
      Map<String, TableDef> tables = new LinkedHashMap<>();
      String sql = "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String tableName = rs.getString("table_name");
               String ddl = this.buildTableDdl(connection, owner, tableName);
               tables.put(tableName, new TableDef(tableName, owner, ddl, this.getColumns(connection, owner, tableName)));
            }
         }
      }
      return tables;
   }

   private Map<String, ColumnDef> getColumns(Connection connection, String owner, String tableName) throws SQLException {
      Map<String, ColumnDef> columns = new LinkedHashMap<>();
      String sql = "SELECT column_name, data_type, data_length, data_precision, data_scale, nullable, data_default, column_id "
         + "FROM all_tab_columns WHERE owner = ? AND table_name = ? ORDER BY column_id";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         stmt.setString(2, tableName);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String name = rs.getString("column_name");
               columns.put(
                  name,
                  new ColumnDef(
                     name,
                     buildDataType(rs),
                     "Y".equals(rs.getString("nullable")),
                     rs.getString("data_default"),
                     rs.getInt("column_id")
                  )
               );
            }
         }
      }
      return columns;
   }

   private Map<String, IndexDef> getIndexes(Connection connection, String owner) throws SQLException {
      Map<String, IndexDef> indexes = new LinkedHashMap<>();
      String sql = "SELECT i.index_name, i.table_name, "
         + "CASE WHEN i.uniqueness = 'UNIQUE' THEN 'CREATE UNIQUE INDEX ' ELSE 'CREATE INDEX ' END "
         + "|| i.owner || '.' || i.index_name || ' ON ' || i.table_owner || '.' || i.table_name || ' (' || "
         + "LISTAGG(c.column_name, ', ') WITHIN GROUP (ORDER BY c.column_position) || ')' AS definition "
         + "FROM all_indexes i "
         + "JOIN all_ind_columns c ON c.index_owner = i.owner AND c.index_name = i.index_name "
         + "WHERE i.owner = ? AND i.generated = 'N' "
         + "GROUP BY i.owner, i.index_name, i.table_owner, i.table_name, i.uniqueness "
         + "ORDER BY i.index_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               indexes.put(rs.getString("index_name"), new IndexDef(rs.getString("index_name"), rs.getString("table_name"), rs.getString("definition")));
            }
         }
      }
      return indexes;
   }

   private Map<String, ConstraintDef> getConstraints(Connection connection, String owner) throws SQLException {
      Map<String, ConstraintDef> constraints = new LinkedHashMap<>();
      String sql = "SELECT c.constraint_name, c.table_name, c.constraint_type, "
         + "CASE c.constraint_type "
         + "WHEN 'P' THEN 'PRIMARY KEY (' || cols.column_list || ')' "
         + "WHEN 'U' THEN 'UNIQUE (' || cols.column_list || ')' "
         + "WHEN 'R' THEN 'FOREIGN KEY (' || cols.column_list || ') REFERENCES ' || c.r_owner || '.' || rc.table_name || ' (' || rcols.column_list || ')' "
         + "ELSE NULL END AS definition "
         + "FROM all_constraints c "
         + "JOIN (SELECT owner, constraint_name, LISTAGG(column_name, ', ') WITHIN GROUP (ORDER BY position) AS column_list "
         + "      FROM all_cons_columns GROUP BY owner, constraint_name) cols "
         + "  ON cols.owner = c.owner AND cols.constraint_name = c.constraint_name "
         + "LEFT JOIN all_constraints rc ON rc.owner = c.r_owner AND rc.constraint_name = c.r_constraint_name "
         + "LEFT JOIN (SELECT owner, constraint_name, LISTAGG(column_name, ', ') WITHIN GROUP (ORDER BY position) AS column_list "
         + "           FROM all_cons_columns GROUP BY owner, constraint_name) rcols "
         + "  ON rcols.owner = rc.owner AND rcols.constraint_name = rc.constraint_name "
         + "WHERE c.owner = ? AND c.constraint_type IN ('P', 'U', 'R') "
         + "ORDER BY c.constraint_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String type = switch (rs.getString("constraint_type")) {
                  case "P" -> "PRIMARY KEY";
                  case "U" -> "UNIQUE";
                  case "R" -> "FOREIGN KEY";
                  default -> rs.getString("constraint_type");
               };
               constraints.put(
                  rs.getString("constraint_name"),
                  new ConstraintDef(rs.getString("constraint_name"), rs.getString("table_name"), type, rs.getString("definition"))
               );
            }
         }
      }
      return constraints;
   }

   private Map<String, ViewDef> getViews(Connection connection, String owner) throws SQLException {
      Map<String, ViewDef> views = new LinkedHashMap<>();
      String sql = "SELECT view_name, text FROM all_views WHERE owner = ? ORDER BY view_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, owner);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               views.put(rs.getString("view_name"), new ViewDef(rs.getString("view_name"), rs.getString("text")));
            }
         }
      }
      return views;
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
}
