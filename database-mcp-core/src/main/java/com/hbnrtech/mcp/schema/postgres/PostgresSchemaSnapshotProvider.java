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
      StringBuilder sb = new StringBuilder();
      sb.append("CREATE TABLE ").append(this.quoteIdentifier(resolvedSchema)).append(".").append(this.quoteIdentifier(tableName)).append(" (\n");
      Map<String, ColumnDef> columns = this.getColumns(connection, resolvedSchema, tableName);
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
      this.appendCommentStatements(connection, resolvedSchema, tableName, sb);
      return sb.toString();
   }

   private void appendCommentStatements(Connection connection, String schema, String tableName, StringBuilder sb) throws SQLException {
      String tableComment = this.getTableComment(connection, schema, tableName);
      Map<String, String> columnComments = this.getColumnComments(connection, schema, tableName);
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

   private Map<String, TableDef> getTables(Connection connection, String schema) throws SQLException {
      Map<String, TableDef> tables = new LinkedHashMap<>();
      String sql = "SELECT t.table_name FROM information_schema.tables t WHERE t.table_schema = ? AND t.table_type = 'BASE TABLE' ORDER BY t.table_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String tableName = rs.getString("table_name");
               String ddl = this.buildTableDdl(connection, schema, tableName);
               tables.put(tableName, new TableDef(tableName, schema, ddl, this.getColumns(connection, schema, tableName)));
            }
         }
      }
      return tables;
   }

   private Map<String, ColumnDef> getColumns(Connection connection, String schema, String tableName) throws SQLException {
      Map<String, ColumnDef> columns = new LinkedHashMap<>();
      String sql = "SELECT c.column_name, c.data_type, c.character_maximum_length, c.numeric_precision, c.numeric_scale, c.is_nullable, c.column_default, c.ordinal_position "
         + "FROM information_schema.columns c WHERE c.table_schema = ? AND c.table_name = ? ORDER BY c.ordinal_position";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         stmt.setString(2, tableName);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               columns.put(
                  rs.getString("column_name"),
                  new ColumnDef(
                     rs.getString("column_name"),
                     buildDataType(rs),
                     "YES".equals(rs.getString("is_nullable")),
                     rs.getString("column_default"),
                     rs.getInt("ordinal_position")
                  )
               );
            }
         }
      }
      return columns;
   }

   private String getTableComment(Connection connection, String schema, String tableName) throws SQLException {
      String sql = "SELECT obj_description(c.oid, 'pg_class') AS table_comment "
         + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
         + "WHERE n.nspname = ? AND c.relname = ? AND c.relkind IN ('r', 'p')";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         stmt.setString(2, tableName);
         try (ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getString("table_comment") : null;
         }
      }
   }

   private Map<String, String> getColumnComments(Connection connection, String schema, String tableName) throws SQLException {
      Map<String, String> comments = new LinkedHashMap<>();
      String sql = "SELECT a.attname, col_description(c.oid, a.attnum) AS column_comment "
         + "FROM pg_attribute a "
         + "JOIN pg_class c ON c.oid = a.attrelid "
         + "JOIN pg_namespace n ON n.oid = c.relnamespace "
         + "WHERE n.nspname = ? AND c.relname = ? AND a.attnum > 0 AND NOT a.attisdropped "
         + "ORDER BY a.attnum";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         stmt.setString(2, tableName);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String comment = rs.getString("column_comment");
               if (comment != null && !comment.isBlank()) {
                  comments.put(rs.getString("attname"), comment);
               }
            }
         }
      }
      return comments;
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
