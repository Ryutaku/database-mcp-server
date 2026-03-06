package com.example.mcp.dialect;

import com.example.mcp.config.DatabaseType;
import com.example.mcp.execution.SqlSafetyPolicy;
import com.example.mcp.schema.SchemaSnapshotProvider;
import com.example.mcp.schema.SyncScriptGenerator;
import com.zaxxer.hikari.HikariConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DatabaseDialect {
   DatabaseType type();

   String serverName();

   DialectCapabilities capabilities();

   void configureDataSource(HikariConfig config);

   void applySessionContext(Connection connection, String activeSchema) throws SQLException;

   String quoteIdentifier(String identifier);

   boolean isSafeIdentifier(String identifier);

   String sqlListSchemas();

   String sqlListTables();

   String sqlDescribeTable();

   String sqlDbInfo();

   String sqlCurrentUser();

   Optional<String> sqlListIndexes(boolean filterByTable);

   Optional<String> sqlAnalyzeIndexes(boolean filterByTable);

   Optional<String> sqlSchemaExists();

   Optional<String> sqlTableExists();

   Optional<String> sqlIndexExists();

   String buildCreateSchemaSql(String schema, boolean ifNotExists);

   String buildCreateTableSql(String schema, String tableName, List<Map<String, Object>> columns, boolean ifNotExists);

   String buildAlterTableSql(String schema, String tableName, String action, Map<String, Object> args);

   String buildDropTableSql(String schema, String tableName, boolean ifExists, boolean cascade);

   String buildCreateIndexSql(String schema, String tableName, String indexName, List<String> columns, boolean unique, boolean ifNotExists);

   String buildDropIndexSql(String schema, String indexName, boolean ifExists);

   SchemaSnapshotProvider snapshotProvider();

   SyncScriptGenerator syncScriptGenerator();

   SqlSafetyPolicy safetyPolicy();
}
