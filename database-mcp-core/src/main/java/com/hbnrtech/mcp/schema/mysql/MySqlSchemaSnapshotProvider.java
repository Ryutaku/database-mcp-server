package com.hbnrtech.mcp.schema.mysql;

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
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public class MySqlSchemaSnapshotProvider implements SchemaSnapshotProvider {
   @Override
   public SchemaSnapshot loadSnapshot(Connection connection, String schema) throws SQLException {
      String database = normalizeDatabase(connection, schema);
      Map<String, TableDef> tables = new LinkedHashMap<>();
      String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, database);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String tableName = rs.getString("table_name");
               tables.put(tableName, new TableDef(tableName, database, this.buildTableDdl(connection, database, tableName), this.getColumns(connection, database, tableName)));
            }
         }
      }

      return new SchemaSnapshot(database, tables, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
   }

   @Override
   public String buildTableDdl(Connection connection, String schema, String tableName) throws SQLException {
      String database = normalizeDatabase(connection, schema);
      String sql = "SHOW CREATE TABLE " + this.quoteIdentifier(database) + "." + this.quoteIdentifier(tableName);
      try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
         if (rs.next()) {
            return rs.getString("Create Table");
         }
      }
      throw new SQLException("Table not found: " + tableName);
   }

   private Map<String, ColumnDef> getColumns(Connection connection, String database, String tableName) throws SQLException {
      Map<String, ColumnDef> columns = new LinkedHashMap<>();
      String sql = "SELECT column_name, column_type, is_nullable, column_default, ordinal_position "
         + "FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
      try (PreparedStatement stmt = connection.prepareStatement(sql)) {
         stmt.setString(1, database);
         stmt.setString(2, tableName);
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               String name = rs.getString("column_name");
               columns.put(
                  name,
                  new ColumnDef(
                     name,
                     rs.getString("column_type"),
                     "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                     rs.getString("column_default"),
                     rs.getInt("ordinal_position")
                  )
               );
            }
         }
      }
      return columns;
   }

   private static String normalizeDatabase(Connection connection, String schema) throws SQLException {
      if (schema != null && !schema.isBlank()) {
         return schema;
      }
      String catalog = connection.getCatalog();
      if (catalog != null && !catalog.isBlank()) {
         return catalog;
      }
      try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT DATABASE()")) {
         if (rs.next()) {
            String database = rs.getString(1);
            if (database != null && !database.isBlank()) {
               return database;
            }
         }
      }
      throw new SQLException("No active MySQL database selected");
   }

   private String quoteIdentifier(String identifier) {
      return "`" + identifier.replace("`", "``") + "`";
   }
}
