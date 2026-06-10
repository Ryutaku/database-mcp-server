package com.hbnrtech.mcp.dialect.mysql;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.dialect.DatabaseDialect;
import com.hbnrtech.mcp.dialect.DialectCapabilities;
import com.hbnrtech.mcp.execution.SqlSafetyPolicy;
import com.hbnrtech.mcp.schema.SchemaSnapshotProvider;
import com.hbnrtech.mcp.schema.SyncScriptGenerator;
import com.hbnrtech.mcp.schema.mysql.MySqlSchemaSnapshotProvider;
import com.hbnrtech.mcp.schema.mysql.MySqlSyncScriptGenerator;
import com.zaxxer.hikari.HikariConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MySqlDialect implements DatabaseDialect {
   private final SqlSafetyPolicy safetyPolicy = new MySqlSafetyPolicy();
   private final SchemaSnapshotProvider snapshotProvider = new MySqlSchemaSnapshotProvider();
   private final SyncScriptGenerator syncScriptGenerator = new MySqlSyncScriptGenerator();

   @Override
   public DatabaseType type() {
      return DatabaseType.MYSQL;
   }

   @Override
   public String serverName() {
      return "database-mcp-server";
   }

   @Override
   public DialectCapabilities capabilities() {
      return new DialectCapabilities(true, true, false, true, false);
   }

   @Override
   public void configureDataSource(HikariConfig config) {
      config.setConnectionTestQuery("SELECT 1");
      config.addDataSourceProperty("cachePrepStmts", "true");
      config.addDataSourceProperty("prepStmtCacheSize", "250");
      config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
      config.addDataSourceProperty("useUnicode", "true");
      config.addDataSourceProperty("characterEncoding", "utf8");
   }

   @Override
   public void applySessionContext(Connection connection, String activeSchema) throws SQLException {
      try (Statement stmt = connection.createStatement()) {
         stmt.execute("USE " + this.quoteIdentifier(activeSchema));
      }
   }

   @Override
   public String quoteIdentifier(String identifier) {
      return "`" + identifier.replace("`", "``") + "`";
   }

   @Override
   public boolean isSafeIdentifier(String identifier) {
      return identifier != null && identifier.matches("[A-Za-z_][A-Za-z0-9_]*");
   }

   @Override
   public String sqlListSchemas() {
      return "SELECT schema_name, default_character_set_name, default_collation_name,\n"
         + "       (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = s.schema_name) AS table_count\n"
         + "FROM information_schema.schemata s\n"
         + "WHERE schema_name NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')\n"
         + "ORDER BY schema_name";
   }

   @Override
   public String sqlListTables() {
      return "SELECT t.table_name, t.table_comment,\n"
         + "       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = t.table_schema AND table_name = t.table_name) AS column_count\n"
         + "FROM information_schema.tables t\n"
         + "WHERE t.table_schema = DATABASE() AND t.table_type = 'BASE TABLE'\n"
         + "ORDER BY t.table_name";
   }

   @Override
   public String sqlDescribeTable() {
      return "SELECT c.column_name, c.data_type, c.character_maximum_length, c.numeric_precision, c.numeric_scale,\n"
         + "       c.is_nullable, c.column_default, c.column_comment\n"
         + "FROM information_schema.columns c\n"
         + "WHERE c.table_schema = DATABASE() AND c.table_name = ?\n"
         + "ORDER BY c.ordinal_position";
   }

   @Override
   public String sqlDbInfo() {
      return "SELECT DATABASE() AS database_name,\n"
         + "       VERSION() AS version,\n"
         + "       @@hostname AS hostname,\n"
         + "       @@port AS port,\n"
         + "       @@character_set_database AS database_charset,\n"
         + "       @@collation_database AS database_collation";
   }

   @Override
   public String sqlCurrentUser() {
      return "SELECT CURRENT_USER() AS username,\n"
         + "       USER() AS login_user,\n"
         + "       DATABASE() AS current_schema";
   }

   @Override
   public Optional<String> sqlListIndexes(boolean filterByTable) {
      String base = "SELECT table_name, index_name,\n"
         + "       GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ', ') AS index_columns,\n"
         + "       CASE WHEN non_unique = 0 THEN 'UNIQUE' ELSE 'NON_UNIQUE' END AS uniqueness\n"
         + "FROM information_schema.statistics\n"
         + "WHERE table_schema = DATABASE()";
      if (filterByTable) {
         return Optional.of(base + " AND table_name = ?\nGROUP BY table_name, index_name, non_unique\nORDER BY index_name");
      }
      return Optional.of(base + "\nGROUP BY table_name, index_name, non_unique\nORDER BY table_name, index_name");
   }

   @Override
   public Optional<String> sqlAnalyzeIndexes(boolean filterByTable) {
      return Optional.empty();
   }

   @Override
   public Optional<String> sqlSchemaExists() {
      return Optional.of("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?");
   }

   @Override
   public Optional<String> sqlTableExists() {
      return Optional.of("SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?");
   }

   @Override
   public Optional<String> sqlIndexExists() {
      return Optional.of("SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND index_name = ?");
   }

   @Override
   public String buildCreateSchemaSql(String schema, boolean ifNotExists) {
      return "CREATE SCHEMA " + (ifNotExists ? "IF NOT EXISTS " : "") + this.quoteIdentifier(schema);
   }

   @Override
   public String buildCreateTableSql(String schema, String tableName, List<Map<String, Object>> columns, boolean ifNotExists) {
      StringBuilder sql = new StringBuilder("CREATE TABLE ");
      if (ifNotExists) {
         sql.append("IF NOT EXISTS ");
      }
      sql.append(this.quoteIdentifier(tableName)).append(" (");

      List<String> columnDefs = new ArrayList<>();
      for (Map<String, Object> col : columns) {
         columnDefs.add(this.columnDefinition(col, true));
      }

      sql.append(String.join(", ", columnDefs)).append(")");
      return sql.toString();
   }

   @Override
   public String buildAlterTableSql(String schema, String tableName, String action, Map<String, Object> args) {
      String fullTableName = this.quoteIdentifier(tableName);
      return switch (action) {
         case "add_column" -> "ALTER TABLE " + fullTableName + " ADD COLUMN " + this.columnDefinition(castMap(args.get("columnDef")), false);
         case "drop_column" -> "ALTER TABLE " + fullTableName + " DROP COLUMN " + this.quoteIdentifier((String)args.get("columnName"));
         case "rename_column" -> "ALTER TABLE "
            + fullTableName
            + " RENAME COLUMN "
            + this.quoteIdentifier((String)args.get("columnName"))
            + " TO "
            + this.quoteIdentifier((String)args.get("newColumnName"));
         case "alter_column" -> "ALTER TABLE " + fullTableName + " MODIFY COLUMN " + this.columnDefinition(castMap(args.get("columnDef")), false);
         case "add_constraint" -> "ALTER TABLE "
            + fullTableName
            + " ADD CONSTRAINT "
            + this.quoteIdentifier((String)args.get("constraintName"))
            + " "
            + args.get("constraintDef");
         case "drop_constraint" -> "ALTER TABLE " + fullTableName + " DROP CONSTRAINT " + this.quoteIdentifier((String)args.get("constraintName"));
         default -> throw new IllegalArgumentException("Unsupported alter table action: " + action);
      };
   }

   @Override
   public String buildDropTableSql(String schema, String tableName, boolean ifExists, boolean cascade) {
      return "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + this.quoteIdentifier(tableName);
   }

   @Override
   public String buildCreateIndexSql(String schema, String tableName, String indexName, List<String> columns, boolean unique, boolean ifNotExists) {
      String cols = String.join(", ", columns.stream().map(this::quoteIdentifier).toList());
      return (unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
         + this.quoteIdentifier(indexName)
         + " ON "
         + this.quoteIdentifier(tableName)
         + " ("
         + cols
         + ")";
   }

   @Override
   public String buildDropIndexSql(String schema, String tableName, String indexName, boolean ifExists) {
      if (tableName == null || tableName.isBlank()) {
         throw new IllegalArgumentException("tableName is required when dropping a MySQL index");
      }
      return "DROP INDEX " + this.quoteIdentifier(indexName) + " ON " + this.quoteIdentifier(tableName);
   }

   @Override
   public SchemaSnapshotProvider snapshotProvider() {
      return this.snapshotProvider;
   }

   @Override
   public SyncScriptGenerator syncScriptGenerator() {
      return this.syncScriptGenerator;
   }

   @Override
   public SqlSafetyPolicy safetyPolicy() {
      return this.safetyPolicy;
   }

   private String columnDefinition(Map<String, Object> col, boolean includePrimaryKey) {
      StringBuilder colDef = new StringBuilder();
      colDef.append(this.quoteIdentifier((String)col.get("name")));
      colDef.append(" ").append(col.get("type"));
      if (Boolean.TRUE.equals(col.get("notNull"))) {
         colDef.append(" NOT NULL");
      }
      if (col.get("default") != null) {
         colDef.append(" DEFAULT ").append(col.get("default"));
      }
      if (includePrimaryKey && Boolean.TRUE.equals(col.get("primaryKey"))) {
         colDef.append(" PRIMARY KEY");
      }
      return colDef.toString();
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> castMap(Object value) {
      return (Map<String, Object>)value;
   }
}
