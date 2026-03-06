package com.example.mcp.tools;

import com.example.mcp.config.DatabaseType;
import com.example.mcp.dialect.DatabaseDialect;
import com.example.mcp.execution.ConnectionManager;
import com.example.mcp.execution.JdbcExecutor;
import com.example.mcp.schema.SchemaDiffEngine;
import com.example.mcp.schema.SchemaDiffResult;
import com.example.mcp.schema.SchemaSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class GenericMcpTools {
   private final DatabaseDialect dialect;
   private final ConnectionManager connectionManager;
   private final JdbcExecutor executor;
   private static final ObjectMapper TOOL_SCHEMA_MAPPER = new ObjectMapper();
   private final SchemaDiffEngine schemaDiffEngine;
   // 当前会话的活动 schema，由 db_switch_schema 动态更新。
   private volatile String activeSchema;

   public GenericMcpTools(DatabaseDialect dialect, ConnectionManager connectionManager, JdbcExecutor executor, String defaultSchema) {
      this.dialect = dialect;
      this.connectionManager = connectionManager;
      this.executor = executor;
      this.schemaDiffEngine = new SchemaDiffEngine();
      this.activeSchema = defaultSchema;
   }

   public List<RegisteredTool> getRegisteredTools() {
      List<RegisteredTool> tools = new ArrayList<>();
      // 先注册统一的 db_* 工具，再为兼容老客户端补充 pg_* 别名。
      tools.add(new RegisteredTool(this.tool("db_query", "Execute a read-only SELECT or WITH query", """
         {
           "type": "object",
           "properties": {
             "sql": {"type": "string", "description": "Read-only SQL statement"}
           },
           "required": ["sql"]
         }
         """), this.queryHandler()));
      tools.add(new RegisteredTool(this.tool("db_list_schemas", "List schemas", "{\"type\":\"object\",\"properties\":{}}"), this.listSchemasHandler()));
      tools.add(new RegisteredTool(this.tool("db_create_schema", "Create a schema", """
         {
           "type": "object",
           "properties": {
             "schema": {"type": "string"},
             "ifNotExists": {"type": "boolean", "default": true}
           },
           "required": ["schema"]
         }
         """), this.createSchemaHandler()));
      tools.add(new RegisteredTool(this.tool("db_switch_schema", "Switch the active schema for following requests", """
         {
           "type": "object",
           "properties": {
             "schema": {"type": "string"}
           },
           "required": ["schema"]
         }
         """), this.switchSchemaHandler()));
      tools.add(new RegisteredTool(this.tool("db_list_tables", "List tables in a schema", """
         {
           "type": "object",
           "properties": {
             "schema": {"type": "string"}
           }
         }
         """), this.listTablesHandler()));
      tools.add(new RegisteredTool(this.tool("db_describe_table", "Describe table columns and metadata", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"},
             "schema": {"type": "string"}
           },
           "required": ["tableName"]
         }
         """), this.describeTableHandler()));
      tools.add(new RegisteredTool(this.tool("db_create_table", "Create a table from column definitions", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"},
             "schema": {"type": "string"},
             "columns": {"type": "array", "items": {"type": "object"}},
             "ifNotExists": {"type": "boolean", "default": true}
           },
           "required": ["tableName", "columns"]
         }
         """), this.createTableHandler()));
      tools.add(new RegisteredTool(this.tool("db_alter_table", "Alter table structure", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"},
             "schema": {"type": "string"},
             "action": {"type": "string"},
             "columnDef": {"type": "object"},
             "columnName": {"type": "string"},
             "newColumnName": {"type": "string"},
             "constraintName": {"type": "string"},
             "constraintDef": {"type": "string"}
           },
           "required": ["tableName", "action"]
         }
         """), this.alterTableHandler()));
      tools.add(new RegisteredTool(this.tool("db_drop_table", "Drop a table", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"},
             "schema": {"type": "string"},
             "ifExists": {"type": "boolean", "default": true},
             "cascade": {"type": "boolean", "default": false}
           },
           "required": ["tableName"]
         }
         """), this.dropTableHandler()));
      tools.add(new RegisteredTool(this.tool("db_get_ddl", "Generate table DDL", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"},
             "schema": {"type": "string"}
           },
           "required": ["tableName"]
         }
         """), this.getDdlHandler()));
      tools.add(new RegisteredTool(this.tool("db_list_indexes", "List indexes", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"},
             "schema": {"type": "string"}
           }
         }
         """), this.listIndexesHandler()));
      tools.add(new RegisteredTool(this.tool("db_create_index", "Create an index", """
         {
           "type": "object",
           "properties": {
             "indexName": {"type": "string"},
             "tableName": {"type": "string"},
             "schema": {"type": "string"},
             "columns": {"type": "array", "items": {"type": "string"}},
             "unique": {"type": "boolean", "default": false},
             "ifNotExists": {"type": "boolean", "default": true}
           },
           "required": ["indexName", "tableName", "columns"]
         }
         """), this.createIndexHandler()));
      tools.add(new RegisteredTool(this.tool("db_drop_index", "Drop an index", """
         {
           "type": "object",
           "properties": {
             "indexName": {"type": "string"},
             "schema": {"type": "string"},
             "ifExists": {"type": "boolean", "default": true}
           },
           "required": ["indexName"]
         }
         """), this.dropIndexHandler()));
      tools.add(new RegisteredTool(this.tool("db_analyze_index", "Analyze index usage", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"},
             "schema": {"type": "string"}
           }
         }
         """), this.analyzeIndexHandler()));
      tools.add(new RegisteredTool(this.tool("db_execute", "Execute DDL or DML SQL", """
         {
           "type": "object",
           "properties": {
             "sql": {"type": "string"}
           },
           "required": ["sql"]
         }
         """), this.executeHandler()));
      tools.add(new RegisteredTool(this.tool("db_info", "Show database information", "{\"type\":\"object\",\"properties\":{}}"), this.dbInfoHandler()));
      tools.add(new RegisteredTool(this.tool("db_current_user", "Show current user and session information", "{\"type\":\"object\",\"properties\":{}}"), this.currentUserHandler()));
      tools.add(new RegisteredTool(this.tool("db_compare_schemas", "Compare two schemas and generate sync SQL", """
         {
           "type": "object",
           "properties": {
             "sourceSchema": {"type": "string"},
             "targetSchema": {"type": "string"}
           },
           "required": ["sourceSchema", "targetSchema"]
         }
         """), this.compareSchemasHandler()));
      tools.addAll(this.aliasTools(tools));
      return tools;
   }

   private McpSchema.Tool tool(String name, String description, String schema) {
      return new McpSchema.Tool(name, null, description, this.parseJsonSchema(schema), null, null, null);
   }

   private McpSchema.JsonSchema parseJsonSchema(String schema) {
      try {
         return TOOL_SCHEMA_MAPPER.readValue(schema, new TypeReference<McpSchema.JsonSchema>() { });
      } catch (JsonProcessingException ex) {
         throw new IllegalArgumentException("Invalid tool schema JSON", ex);
      }
   }

   private List<RegisteredTool> aliasTools(List<RegisteredTool> primaryTools) {
      List<RegisteredTool> aliases = new ArrayList<>();
      for (RegisteredTool primary : primaryTools) {
         String primaryName = primary.tool().name();
         if (primaryName.startsWith("db_")) {
            // 保留历史 PostgreSQL 命名，减少客户端迁移成本。
            String aliasName = primaryName.equals("db_info") ? "pg_db_info" : "pg_" + primaryName.substring(3);
            aliases.add(
               new RegisteredTool(
                  new McpSchema.Tool(aliasName, primary.tool().title(), primary.tool().description(), primary.tool().inputSchema(), primary.tool().outputSchema(), primary.tool().annotations(), primary.tool().meta()),
                  primary.handler()
               )
            );
         }
      }
      return aliases;
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> queryHandler() {
      return args -> {
         String sql = (String)args.get("sql");
         Optional<String> validation = this.dialect.safetyPolicy().validateReadOnly(sql);
         // 只允许只读查询，避免把查询工具误用成写入入口。
         return validation.isPresent() ? ToolResults.error(validation.get()) : this.executeQuery(sql);
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> listSchemasHandler() {
      return args -> this.executeQuery(this.dialect.sqlListSchemas());
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> createSchemaHandler() {
      return args -> {
         if (!this.dialect.capabilities().createSchema()) {
            return ToolResults.error("Schema creation is not supported for " + this.dialect.type());
         }

         String schema = (String)args.get("schema");
         if (!this.dialect.isSafeIdentifier(schema)) {
            return ToolResults.error("Invalid schema identifier");
         }

         boolean ifNotExists = (Boolean)args.getOrDefault("ifNotExists", true);
         return this.executeDdl(this.dialect.buildCreateSchemaSql(schema, ifNotExists), "Schema '" + schema + "' created successfully");
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> switchSchemaHandler() {
      return args -> {
         if (!this.dialect.capabilities().switchSchema()) {
            return ToolResults.error("Schema switching is not supported for " + this.dialect.type());
         }

         String schema = (String)args.get("schema");
         if (!this.dialect.isSafeIdentifier(schema)) {
            return ToolResults.error("Invalid schema identifier");
         }

         this.activeSchema = schema;
         // 这里只更新服务端会话状态，真正执行时再通过 ConnectionManager 应用到连接。
         return ToolResults.success("Active schema set to: " + schema);
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> listTablesHandler() {
      return args -> this.executePreparedQuery(this.dialect.sqlListTables(), List.of(this.schemaArg(args)));
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> describeTableHandler() {
      return args -> this.executePreparedQuery(this.dialect.sqlDescribeTable(), List.of(this.schemaArg(args), args.get("tableName")));
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> createTableHandler() {
      return args -> {
         String schema = this.schemaArg(args);
         String tableName = (String)args.get("tableName");
         @SuppressWarnings("unchecked")
         List<Map<String, Object>> columns = (List<Map<String, Object>>)args.get("columns");
         boolean ifNotExists = (Boolean)args.getOrDefault("ifNotExists", true);
         // 已存在时直接返回成功，保持工具调用幂等。
         if (ifNotExists && this.objectExists(this.dialect.sqlTableExists(), List.of(this.normalizeIdentifierValue(schema), this.normalizeIdentifierValue(tableName)))) {
            return ToolResults.success("Table '" + schema + "." + tableName + "' already exists");
         }
         return this.executeDdl(
            this.dialect.buildCreateTableSql(schema, tableName, columns, ifNotExists),
            "Table '" + schema + "." + tableName + "' created successfully"
         );
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> alterTableHandler() {
      return args -> {
         String schema = this.schemaArg(args);
         String tableName = (String)args.get("tableName");
         String action = (String)args.get("action");
         return this.executeDdl(
            this.dialect.buildAlterTableSql(schema, tableName, action, args),
            "Table '" + schema + "." + tableName + "' altered successfully"
         );
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> dropTableHandler() {
      return args -> {
         String schema = this.schemaArg(args);
         String tableName = (String)args.get("tableName");
         boolean ifExists = (Boolean)args.getOrDefault("ifExists", true);
         boolean cascade = (Boolean)args.getOrDefault("cascade", false);
         if (ifExists && !this.objectExists(this.dialect.sqlTableExists(), List.of(this.normalizeIdentifierValue(schema), this.normalizeIdentifierValue(tableName)))) {
            return ToolResults.success("Table '" + schema + "." + tableName + "' does not exist");
         }
         return this.executeDdl(
            this.dialect.buildDropTableSql(schema, tableName, ifExists, cascade),
            "Table '" + schema + "." + tableName + "' dropped successfully"
         );
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> getDdlHandler() {
      return args -> {
         if (!this.dialect.capabilities().getDdl() || this.dialect.type() != DatabaseType.POSTGRES) {
            return ToolResults.error("DDL generation is not implemented for " + this.dialect.type());
         }

         String schema = this.schemaArg(args);
         String tableName = (String)args.get("tableName");
         try (Connection conn = this.connectionManager.getConnection(this.activeSchema)) {
            return ToolResults.success(this.dialect.snapshotProvider().buildTableDdl(conn, schema, tableName));
         } catch (SQLException ex) {
            return ToolResults.error("Failed to generate DDL: " + ex.getMessage());
         }
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> listIndexesHandler() {
      return args -> {
         String schema = this.schemaArg(args);
         String tableName = (String)args.get("tableName");
         boolean hasTableFilter = tableName != null && !tableName.isBlank();
         Optional<String> sql = this.dialect.sqlListIndexes(hasTableFilter);
         if (sql.isEmpty()) {
            return ToolResults.error("Index listing is not supported for " + this.dialect.type());
         }

         return hasTableFilter ? this.executePreparedQuery(sql.get(), List.of(schema, tableName)) : this.executePreparedQuery(sql.get(), List.of(schema));
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> createIndexHandler() {
      return args -> {
         String schema = this.schemaArg(args);
         String tableName = (String)args.get("tableName");
         String indexName = (String)args.get("indexName");
         @SuppressWarnings("unchecked")
         List<String> columns = (List<String>)args.get("columns");
         boolean unique = (Boolean)args.getOrDefault("unique", false);
         boolean ifNotExists = (Boolean)args.getOrDefault("ifNotExists", true);
         if (ifNotExists && this.objectExists(this.dialect.sqlIndexExists(), List.of(this.normalizeIdentifierValue(schema), this.normalizeIdentifierValue(indexName)))) {
            return ToolResults.success("Index '" + indexName + "' already exists");
         }
         return this.executeDdl(
            this.dialect.buildCreateIndexSql(schema, tableName, indexName, columns, unique, ifNotExists),
            "Index '" + indexName + "' created successfully"
         );
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> dropIndexHandler() {
      return args -> {
         String schema = this.schemaArg(args);
         String indexName = (String)args.get("indexName");
         boolean ifExists = (Boolean)args.getOrDefault("ifExists", true);
         if (ifExists && !this.objectExists(this.dialect.sqlIndexExists(), List.of(this.normalizeIdentifierValue(schema), this.normalizeIdentifierValue(indexName)))) {
            return ToolResults.success("Index '" + indexName + "' does not exist");
         }
         return this.executeDdl(this.dialect.buildDropIndexSql(schema, indexName, ifExists), "Index '" + indexName + "' dropped successfully");
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> analyzeIndexHandler() {
      return args -> {
         if (!this.dialect.capabilities().analyzeIndex()) {
            return ToolResults.error("Index analysis is not supported for " + this.dialect.type());
         }

         String schema = this.schemaArg(args);
         String tableName = (String)args.get("tableName");
         boolean hasTableFilter = tableName != null && !tableName.isBlank();
         Optional<String> sql = this.dialect.sqlAnalyzeIndexes(hasTableFilter);
         if (sql.isEmpty()) {
            return ToolResults.error("Index analysis SQL is not available for " + this.dialect.type());
         }

         return hasTableFilter ? this.executePreparedQuery(sql.get(), List.of(schema, tableName)) : this.executePreparedQuery(sql.get(), List.of(schema));
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> executeHandler() {
      return args -> {
         String sql = (String)args.get("sql");
         Optional<String> validation = this.dialect.safetyPolicy().validateExecute(sql);
         return validation.isPresent() ? ToolResults.error(validation.get()) : this.executeSql(sql);
      };
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> dbInfoHandler() {
      return args -> this.executeQuery(this.dialect.sqlDbInfo());
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> currentUserHandler() {
      return args -> this.executeQuery(this.dialect.sqlCurrentUser());
   }

   private Function<Map<String, Object>, McpSchema.CallToolResult> compareSchemasHandler() {
      return args -> {
         if (!this.dialect.capabilities().compareSchemas() || this.dialect.type() != DatabaseType.POSTGRES) {
            return ToolResults.error("Schema compare is not implemented for " + this.dialect.type());
         }

         String sourceSchema = (String)args.get("sourceSchema");
         String targetSchema = (String)args.get("targetSchema");
         if (sourceSchema == null || sourceSchema.isBlank() || targetSchema == null || targetSchema.isBlank()) {
            return ToolResults.error("Both sourceSchema and targetSchema are required");
         }
         if (sourceSchema.equals(targetSchema)) {
            return ToolResults.error("sourceSchema and targetSchema must be different");
         }

         try (Connection conn = this.connectionManager.getConnection(this.activeSchema)) {
            // 先分别抓取两个 schema 快照，再交给通用 diff 引擎比较。
            SchemaSnapshot source = this.dialect.snapshotProvider().loadSnapshot(conn, sourceSchema);
            SchemaSnapshot target = this.dialect.snapshotProvider().loadSnapshot(conn, targetSchema);
            SchemaDiffResult result = this.schemaDiffEngine.compare(source, target);
            result.syncScripts().addAll(this.dialect.syncScriptGenerator().generateScripts(result));
            return ToolResults.success(this.formatCompareReport(result));
         } catch (SQLException ex) {
            return ToolResults.error("Schema compare failed: " + ex.getMessage());
         }
      };
   }

   private String schemaArg(Map<String, Object> args) {
      Object schema = args.get("schema");
      if (schema instanceof String value && !value.isBlank()) {
         return value;
      }
      if (this.activeSchema != null && !this.activeSchema.isBlank()) {
         return this.activeSchema;
      }
      // PostgreSQL 兜底到 public，Oracle 则允许返回空串交由方言层决定。
      return this.dialect.type() == DatabaseType.POSTGRES ? "public" : "";
   }

   private McpSchema.CallToolResult executeQuery(String sql) {
      try (Connection conn = this.connectionManager.getConnection(this.activeSchema)) {
         return ToolResults.success(this.executor.toJson(this.executor.query(conn, sql)));
      } catch (SQLException ex) {
         return this.sqlError(ex);
      } catch (JsonProcessingException ex) {
         return ToolResults.error("Failed to serialize query result: " + ex.getMessage());
      }
   }

   private McpSchema.CallToolResult executePreparedQuery(String sql, List<Object> params) {
      try (Connection conn = this.connectionManager.getConnection(this.activeSchema)) {
         return ToolResults.success(this.executor.toJson(this.executor.query(conn, sql, params)));
      } catch (SQLException ex) {
         return this.sqlError(ex);
      } catch (JsonProcessingException ex) {
         return ToolResults.error("Failed to serialize query result: " + ex.getMessage());
      }
   }

   private McpSchema.CallToolResult executeDdl(String sql, String successMessage) {
      Optional<String> validation = this.dialect.safetyPolicy().validateExecute(sql);
      if (validation.isPresent()) {
         return ToolResults.error(validation.get());
      }

      try (Connection conn = this.connectionManager.getConnection(this.activeSchema)) {
         // 即使 SQL 由工具拼装，也仍然走统一安全校验和执行入口。
         this.executor.execute(conn, sql);
         return ToolResults.success(successMessage);
      } catch (SQLException ex) {
         return this.sqlError(ex);
      }
   }

   private McpSchema.CallToolResult executeSql(String sql) {
      try (Connection conn = this.connectionManager.getConnection(this.activeSchema)) {
         JdbcExecutor.StatementResult result = this.executor.execute(conn, sql);
         if (result.hasResultSet()) {
            return ToolResults.success("Execution succeeded. Returned rows:\n" + this.executor.toJson(result.rows()));
         }

         return ToolResults.success("Execution succeeded. Rows affected: " + result.updateCount());
      } catch (SQLException ex) {
         return this.sqlError(ex);
      } catch (JsonProcessingException ex) {
         return ToolResults.error("Failed to serialize result set: " + ex.getMessage());
      }
   }

   private McpSchema.CallToolResult sqlError(SQLException ex) {
      if (this.executor.isTimeoutException(ex)) {
         return ToolResults.error("SQL execution timed out: " + ex.getMessage());
      }
      return ToolResults.error("SQL error: " + ex.getMessage());
   }

   private boolean objectExists(Optional<String> sqlOpt, List<Object> params) {
      if (sqlOpt.isEmpty()) {
         return false;
      }

      try (Connection conn = this.connectionManager.getConnection(this.activeSchema)) {
         return !this.executor.query(conn, sqlOpt.get(), params).isEmpty();
      } catch (SQLException ex) {
         // 存在性检查失败时按“不存在”处理，避免影响主流程的幂等判断。
         return false;
      }
   }

   private String normalizeIdentifierValue(String value) {
      if (value == null) {
         return null;
      }
      // Oracle 系统视图通常以大写保存对象名，这里统一做一次归一化。
      return this.dialect.type() == DatabaseType.ORACLE ? value.toUpperCase() : value;
   }

   private String formatCompareReport(SchemaDiffResult result) {
      StringBuilder report = new StringBuilder();
      report.append("=== Schema Compare Result ===\n");
      report.append("Source Schema: ").append(result.sourceSchema()).append("\n");
      report.append("Target Schema: ").append(result.targetSchema()).append("\n\n");
      int totalDiffs = result.tableDiffs().size()
         + result.indexDiffs().size()
         + result.constraintDiffs().size()
         + result.viewDiffs().size()
         + result.routineDiffs().size()
         + result.sequenceDiffs().size();
      if (!result.hasDifferences()) {
         report.append("No differences found.\n");
         return report.toString();
      }

      report.append("Differences found: ").append(totalDiffs).append("\n");
      report.append("Tables: ").append(result.tableDiffs().size()).append("\n");
      report.append("Indexes: ").append(result.indexDiffs().size()).append("\n");
      report.append("Constraints: ").append(result.constraintDiffs().size()).append("\n");
      report.append("Views: ").append(result.viewDiffs().size()).append("\n");
      report.append("Routines: ").append(result.routineDiffs().size()).append("\n");
      report.append("Sequences: ").append(result.sequenceDiffs().size()).append("\n\n");
      report.append("=== Sync SQL ===\n\n");
      if (result.syncScripts().isEmpty()) {
         report.append("-- No sync statements generated\n");
      } else {
         report.append("BEGIN;\n\n");
         for (String script : result.syncScripts()) {
            report.append(script).append("\n");
         }
         report.append("\nCOMMIT;\n");
      }
      return report.toString();
   }
}
