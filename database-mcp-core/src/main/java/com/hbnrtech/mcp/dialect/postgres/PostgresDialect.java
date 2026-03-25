package com.hbnrtech.mcp.dialect.postgres;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.dialect.DatabaseDialect;
import com.hbnrtech.mcp.dialect.DialectCapabilities;
import com.hbnrtech.mcp.execution.SqlSafetyPolicy;
import com.hbnrtech.mcp.schema.SchemaSnapshotProvider;
import com.hbnrtech.mcp.schema.SyncScriptGenerator;
import com.hbnrtech.mcp.schema.postgres.PostgresSchemaSnapshotProvider;
import com.hbnrtech.mcp.schema.postgres.PostgresSyncScriptGenerator;
import com.zaxxer.hikari.HikariConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PostgresDialect implements DatabaseDialect {
   private final SqlSafetyPolicy safetyPolicy = new PostgresSqlSafetyPolicy();
   private final SchemaSnapshotProvider snapshotProvider = new PostgresSchemaSnapshotProvider();
   private final SyncScriptGenerator syncScriptGenerator = new PostgresSyncScriptGenerator();

   @Override
   public DatabaseType type() {
      return DatabaseType.POSTGRES;
   }

   @Override
   public String serverName() {
      return "database-mcp-server";
   }

   @Override
   public DialectCapabilities capabilities() {
      return new DialectCapabilities(true, true, true, true, true);
   }

   @Override
   public void configureDataSource(HikariConfig config) {
      config.setConnectionTestQuery("SELECT 1");
      config.addDataSourceProperty("socketTimeout", "60");
      config.addDataSourceProperty("connectTimeout", "30");
      config.addDataSourceProperty("cachePrepStmts", "true");
      config.addDataSourceProperty("prepStmtCacheSize", "250");
      config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
   }

   @Override
   public void applySessionContext(Connection connection, String activeSchema) throws SQLException {
      try (Statement stmt = connection.createStatement()) {
         stmt.execute("SET search_path TO " + this.quoteIdentifier(activeSchema));
      }
   }

   @Override
   public String quoteIdentifier(String identifier) {
      return "\"" + identifier.replace("\"", "\"\"") + "\"";
   }

   @Override
   public boolean isSafeIdentifier(String identifier) {
      return identifier != null && identifier.matches("[A-Za-z_][A-Za-z0-9_]*");
   }

   @Override
   public String sqlListSchemas() {
      return "SELECT schema_name, schema_owner,\n"
         + "       (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = s.schema_name) AS table_count\n"
         + "FROM information_schema.schemata s\n"
         + "WHERE schema_name NOT LIKE 'pg_%' AND schema_name != 'information_schema'\n"
         + "ORDER BY schema_name";
   }

   @Override
   public String sqlListTables() {
      return "SELECT t.table_name, pgd.description AS table_comment,\n"
         + "       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = t.table_schema AND table_name = t.table_name) AS column_count\n"
         + "FROM information_schema.tables t\n"
         + "LEFT JOIN pg_catalog.pg_description pgd ON pgd.objoid = (quote_ident(t.table_schema) || '.' || quote_ident(t.table_name))::regclass::oid AND pgd.objsubid = 0\n"
         + "WHERE t.table_schema = ? AND t.table_type = 'BASE TABLE'\n"
         + "ORDER BY t.table_name";
   }

   @Override
   public String sqlDescribeTable() {
      return "SELECT c.column_name, c.data_type, c.character_maximum_length, c.numeric_precision, c.numeric_scale,\n"
         + "       c.is_nullable, c.column_default, pgd.description AS column_comment\n"
         + "FROM information_schema.columns c\n"
         + "LEFT JOIN pg_catalog.pg_statio_all_tables st ON c.table_schema = st.schemaname AND c.table_name = st.relname\n"
         + "LEFT JOIN pg_catalog.pg_description pgd ON pgd.objoid = st.relid AND pgd.objsubid = c.ordinal_position\n"
         + "WHERE c.table_schema = ? AND c.table_name = ?\n"
         + "ORDER BY c.ordinal_position";
   }

   @Override
   public String sqlDbInfo() {
      return "SELECT current_database() AS database_name,\n"
         + "       version() AS version,\n"
         + "       pg_size_pretty(pg_database_size(current_database())) AS database_size,\n"
         + "       (SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database()) AS active_connections";
   }

   @Override
   public String sqlCurrentUser() {
      return "SELECT current_user AS username,\n"
         + "       session_user AS session_user,\n"
         + "       current_database() AS database,\n"
         + "       current_schema() AS current_schema,\n"
         + "       COALESCE((SELECT r.rolsuper FROM pg_roles r WHERE r.rolname = current_user), false) AS is_superuser";
   }

   @Override
   public Optional<String> sqlListIndexes(boolean filterByTable) {
      if (filterByTable) {
         return Optional.of(
            "SELECT indexname AS index_name, indexdef AS index_definition,\n"
               + "       pg_size_pretty(pg_relation_size(indexrelid)) AS index_size\n"
               + "FROM pg_indexes\n"
               + "JOIN pg_class ON pg_class.relname = indexname\n"
               + "WHERE schemaname = ? AND tablename = ?\n"
               + "ORDER BY indexname"
         );
      }

      return Optional.of(
         "SELECT schemaname, tablename, indexname AS index_name, indexdef AS index_definition\n"
            + "FROM pg_indexes\n"
            + "WHERE schemaname = ?\n"
            + "ORDER BY tablename, indexname"
      );
   }

   @Override
   public Optional<String> sqlAnalyzeIndexes(boolean filterByTable) {
      if (filterByTable) {
         return Optional.of(
            "SELECT schemaname, relname AS table_name, indexrelname AS index_name,\n"
               + "       idx_scan AS index_scans, idx_tup_read AS tuples_read, idx_tup_fetch AS tuples_fetched\n"
               + "FROM pg_stat_user_indexes\n"
               + "WHERE schemaname = ? AND relname = ?\n"
               + "ORDER BY idx_scan DESC"
         );
      }

      return Optional.of(
         "SELECT schemaname, relname AS table_name, indexrelname AS index_name,\n"
            + "       idx_scan AS index_scans, idx_tup_read AS tuples_read, idx_tup_fetch AS tuples_fetched\n"
            + "FROM pg_stat_user_indexes\n"
            + "WHERE schemaname = ?\n"
            + "ORDER BY idx_scan DESC"
      );
   }

   @Override
   public Optional<String> sqlSchemaExists() {
      return Optional.of("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?");
   }

   @Override
   public Optional<String> sqlTableExists() {
      return Optional.of("SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?");
   }

   @Override
   public Optional<String> sqlIndexExists() {
      return Optional.of("SELECT 1 FROM pg_indexes WHERE schemaname = ? AND indexname = ?");
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

      sql.append(this.quoteIdentifier(schema)).append(".").append(this.quoteIdentifier(tableName)).append(" (");
      List<String> columnDefs = new ArrayList<>();

      for (Map<String, Object> col : columns) {
         StringBuilder colDef = new StringBuilder();
         colDef.append(this.quoteIdentifier((String)col.get("name")));
         colDef.append(" ").append(col.get("type"));
         if (Boolean.TRUE.equals(col.get("notNull"))) {
            colDef.append(" NOT NULL");
         }

         if (Boolean.TRUE.equals(col.get("primaryKey"))) {
            colDef.append(" PRIMARY KEY");
         }

         if (col.get("default") != null) {
            colDef.append(" DEFAULT ").append(col.get("default"));
         }

         columnDefs.add(colDef.toString());
      }

      sql.append(String.join(", ", columnDefs)).append(")");
      return sql.toString();
   }

   @Override
   public String buildAlterTableSql(String schema, String tableName, String action, Map<String, Object> args) {
      String fullTableName = this.quoteIdentifier(schema) + "." + this.quoteIdentifier(tableName);
      StringBuilder sql = new StringBuilder("ALTER TABLE ").append(fullTableName).append(" ");
      switch (action) {
         case "add_column" -> {
            Map<String, Object> colDef = castMap(args.get("columnDef"));
            sql.append("ADD COLUMN ").append(this.quoteIdentifier((String)colDef.get("name")));
            sql.append(" ").append(colDef.get("type"));
            if (Boolean.TRUE.equals(colDef.get("notNull"))) {
               sql.append(" NOT NULL");
            }
            if (colDef.get("default") != null) {
               sql.append(" DEFAULT ").append(colDef.get("default"));
            }
         }
         case "drop_column" -> sql.append("DROP COLUMN ").append(this.quoteIdentifier((String)args.get("columnName")));
         case "rename_column" -> sql.append("RENAME COLUMN ")
            .append(this.quoteIdentifier((String)args.get("columnName")))
            .append(" TO ")
            .append(this.quoteIdentifier((String)args.get("newColumnName")));
         case "alter_column" -> {
            Map<String, Object> colDef = castMap(args.get("columnDef"));
            sql.append("ALTER COLUMN ").append(this.quoteIdentifier((String)colDef.get("name"))).append(" TYPE ").append(colDef.get("type"));
         }
         case "add_constraint" -> sql.append("ADD CONSTRAINT ")
            .append(this.quoteIdentifier((String)args.get("constraintName")))
            .append(" ")
            .append(args.get("constraintDef"));
         case "drop_constraint" -> sql.append("DROP CONSTRAINT ").append(this.quoteIdentifier((String)args.get("constraintName")));
         default -> throw new IllegalArgumentException("Unsupported alter table action: " + action);
      }

      return sql.toString();
   }

   @Override
   public String buildDropTableSql(String schema, String tableName, boolean ifExists, boolean cascade) {
      return "DROP TABLE "
         + (ifExists ? "IF EXISTS " : "")
         + this.quoteIdentifier(schema)
         + "."
         + this.quoteIdentifier(tableName)
         + (cascade ? " CASCADE" : "");
   }

   @Override
   public String buildCreateIndexSql(String schema, String tableName, String indexName, List<String> columns, boolean unique, boolean ifNotExists) {
      String cols = String.join(", ", columns.stream().map(this::quoteIdentifier).toList());
      return (unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
         + (ifNotExists ? "IF NOT EXISTS " : "")
         + this.quoteIdentifier(indexName)
         + " ON "
         + this.quoteIdentifier(schema)
         + "."
         + this.quoteIdentifier(tableName)
         + " ("
         + cols
         + ")";
   }

   @Override
   public String buildDropIndexSql(String schema, String indexName, boolean ifExists) {
      return "DROP INDEX " + (ifExists ? "IF EXISTS " : "") + this.quoteIdentifier(schema) + "." + this.quoteIdentifier(indexName);
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

   @SuppressWarnings("unchecked")
   private static Map<String, Object> castMap(Object value) {
      return (Map<String, Object>)value;
   }
}
