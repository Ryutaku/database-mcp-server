package com.hbnrtech.mcp.dialect.oracle;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.dialect.DatabaseDialect;
import com.hbnrtech.mcp.dialect.DialectCapabilities;
import com.hbnrtech.mcp.execution.SqlSafetyPolicy;
import com.hbnrtech.mcp.schema.SchemaSnapshotProvider;
import com.hbnrtech.mcp.schema.SyncScriptGenerator;
import com.hbnrtech.mcp.schema.oracle.OracleSchemaSnapshotProvider;
import com.hbnrtech.mcp.schema.oracle.OracleSyncScriptGenerator;
import com.zaxxer.hikari.HikariConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OracleDialect implements DatabaseDialect {
   private final SqlSafetyPolicy safetyPolicy = new OracleSqlSafetyPolicy();
   private final SchemaSnapshotProvider snapshotProvider = new OracleSchemaSnapshotProvider();
   private final SyncScriptGenerator syncScriptGenerator = new OracleSyncScriptGenerator();

   @Override
   public DatabaseType type() {
      return DatabaseType.ORACLE;
   }

   @Override
   public String serverName() {
      return "database-mcp-server";
   }

   @Override
   public DialectCapabilities capabilities() {
      return new DialectCapabilities(false, true, false, true, true);
   }

   @Override
   public void configureDataSource(HikariConfig config) {
      config.setConnectionTestQuery("SELECT 1 FROM DUAL");
   }

   @Override
   public void applySessionContext(Connection connection, String activeSchema) throws SQLException {
      try (Statement stmt = connection.createStatement()) {
         stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = " + this.quoteIdentifier(activeSchema));
      }
   }

   @Override
   public String quoteIdentifier(String identifier) {
      return "\"" + identifier.replace("\"", "\"\"") + "\"";
   }

   @Override
   public boolean isSafeIdentifier(String identifier) {
      return identifier != null && identifier.matches("[A-Za-z_][A-Za-z0-9_$#]*");
   }

   @Override
   public String sqlListSchemas() {
      return "SELECT username AS schema_name, created AS created_at FROM all_users ORDER BY username";
   }

   @Override
   public String sqlListTables() {
      return "SELECT table_name, NULL AS table_comment,\n"
         + "       (SELECT COUNT(*) FROM all_tab_columns c WHERE c.owner = t.owner AND c.table_name = t.table_name) AS column_count\n"
         + "FROM all_tables t\n"
         + "WHERE owner = ?\n"
         + "ORDER BY table_name";
   }

   @Override
   public String sqlDescribeTable() {
      return "SELECT c.column_name, c.data_type, c.data_length AS character_maximum_length,\n"
         + "       c.data_precision AS numeric_precision, c.data_scale AS numeric_scale,\n"
         + "       CASE WHEN c.nullable = 'Y' THEN 'YES' ELSE 'NO' END AS is_nullable,\n"
         + "       c.data_default AS column_default, cc.comments AS column_comment\n"
         + "FROM all_tab_columns c\n"
         + "LEFT JOIN all_col_comments cc ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.column_name = c.column_name\n"
         + "WHERE c.owner = ? AND c.table_name = ?\n"
         + "ORDER BY c.column_id";
   }

   @Override
   public String sqlDbInfo() {
      return "SELECT SYS_CONTEXT('USERENV', 'DB_NAME') AS database_name,\n"
         + "       SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS current_schema,\n"
         + "       SYS_CONTEXT('USERENV', 'INSTANCE_NAME') AS instance_name\n"
         + "FROM dual";
   }

   @Override
   public String sqlCurrentUser() {
      return "SELECT USER AS username,\n"
         + "       SYS_CONTEXT('USERENV', 'SESSION_USER') AS session_user,\n"
         + "       SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS current_schema\n"
         + "FROM dual";
   }

   @Override
   public Optional<String> sqlListIndexes(boolean filterByTable) {
      if (filterByTable) {
         return Optional.of(
            "SELECT i.index_name,\n"
               + "       LISTAGG(c.column_name, ', ') WITHIN GROUP (ORDER BY c.column_position) AS index_columns,\n"
               + "       i.uniqueness AS uniqueness\n"
               + "FROM all_indexes i\n"
               + "JOIN all_ind_columns c ON c.index_owner = i.owner AND c.index_name = i.index_name\n"
               + "WHERE i.owner = ? AND i.table_name = ?\n"
               + "GROUP BY i.index_name, i.uniqueness\n"
               + "ORDER BY i.index_name"
         );
      }

      return Optional.of(
         "SELECT i.table_name, i.index_name,\n"
            + "       LISTAGG(c.column_name, ', ') WITHIN GROUP (ORDER BY c.column_position) AS index_columns,\n"
            + "       i.uniqueness AS uniqueness\n"
            + "FROM all_indexes i\n"
            + "JOIN all_ind_columns c ON c.index_owner = i.owner AND c.index_name = i.index_name\n"
            + "WHERE i.owner = ?\n"
            + "GROUP BY i.table_name, i.index_name, i.uniqueness\n"
            + "ORDER BY i.table_name, i.index_name"
      );
   }

   @Override
   public Optional<String> sqlAnalyzeIndexes(boolean filterByTable) {
      return Optional.empty();
   }

   @Override
   public Optional<String> sqlSchemaExists() {
      return Optional.of("SELECT 1 FROM all_users WHERE username = ?");
   }

   @Override
   public Optional<String> sqlTableExists() {
      return Optional.of("SELECT 1 FROM all_tables WHERE owner = ? AND table_name = ?");
   }

   @Override
   public Optional<String> sqlIndexExists() {
      return Optional.of("SELECT 1 FROM all_indexes WHERE owner = ? AND index_name = ?");
   }

   @Override
   public String buildCreateSchemaSql(String schema, boolean ifNotExists) {
      throw new UnsupportedOperationException("Oracle does not support PostgreSQL-style CREATE SCHEMA semantics");
   }

   @Override
   public String buildCreateTableSql(String schema, String tableName, List<Map<String, Object>> columns, boolean ifNotExists) {
      StringBuilder sql = new StringBuilder("CREATE TABLE ");
      sql.append(this.quoteIdentifier(schema)).append(".").append(this.quoteIdentifier(tableName)).append(" (");
      boolean first = true;
      for (Map<String, Object> col : columns) {
         if (!first) {
            sql.append(", ");
         }
         first = false;
         sql.append(this.quoteIdentifier((String)col.get("name"))).append(" ").append(col.get("type"));
         if (Boolean.TRUE.equals(col.get("notNull"))) {
            sql.append(" NOT NULL");
         }
         if (col.get("default") != null) {
            sql.append(" DEFAULT ").append(col.get("default"));
         }
      }
      sql.append(")");
      return sql.toString();
   }

   @Override
   public String buildAlterTableSql(String schema, String tableName, String action, Map<String, Object> args) {
      String fullTableName = this.quoteIdentifier(schema) + "." + this.quoteIdentifier(tableName);
      return switch (action) {
         case "add_column" -> {
            Map<String, Object> colDef = castMap(args.get("columnDef"));
            StringBuilder sql = new StringBuilder("ALTER TABLE ").append(fullTableName).append(" ADD (");
            sql.append(this.quoteIdentifier((String)colDef.get("name"))).append(" ").append(colDef.get("type"));
            if (Boolean.TRUE.equals(colDef.get("notNull"))) {
               sql.append(" NOT NULL");
            }
            if (colDef.get("default") != null) {
               sql.append(" DEFAULT ").append(colDef.get("default"));
            }
            yield sql.append(")").toString();
         }
         case "drop_column" -> "ALTER TABLE " + fullTableName + " DROP COLUMN " + this.quoteIdentifier((String)args.get("columnName"));
         case "rename_column" -> "ALTER TABLE "
            + fullTableName
            + " RENAME COLUMN "
            + this.quoteIdentifier((String)args.get("columnName"))
            + " TO "
            + this.quoteIdentifier((String)args.get("newColumnName"));
         case "alter_column" -> {
            Map<String, Object> colDef = castMap(args.get("columnDef"));
            yield "ALTER TABLE "
               + fullTableName
               + " MODIFY ("
               + this.quoteIdentifier((String)colDef.get("name"))
               + " "
               + colDef.get("type")
               + ")";
         }
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
      return "DROP TABLE " + this.quoteIdentifier(schema) + "." + this.quoteIdentifier(tableName) + (cascade ? " CASCADE CONSTRAINTS" : "");
   }

   @Override
   public String buildCreateIndexSql(String schema, String tableName, String indexName, List<String> columns, boolean unique, boolean ifNotExists) {
      String cols = String.join(", ", columns.stream().map(this::quoteIdentifier).toList());
      return (unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
         + this.quoteIdentifier(schema)
         + "."
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
      return "DROP INDEX " + this.quoteIdentifier(schema) + "." + this.quoteIdentifier(indexName);
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
