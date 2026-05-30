package com.hbnrtech.mcp.tools;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.dialect.DatabaseDialect;
import com.hbnrtech.mcp.execution.DatasourceContext;
import com.hbnrtech.mcp.execution.DatasourceRegistry;
import com.hbnrtech.mcp.execution.JdbcExecutor;
import com.hbnrtech.mcp.schema.SchemaDiffEngine;
import com.hbnrtech.mcp.schema.SchemaDiffResult;
import com.hbnrtech.mcp.schema.SchemaSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenericMcpTools {
   private static final ObjectMapper TOOL_SCHEMA_MAPPER = new ObjectMapper();
   private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile("(?is)^CREATE\\s+TABLE\\s+(.+?)\\s*\\(\\s*\\n(.*?)\\n\\)");
   private static final Pattern COMMENT_TABLE_PATTERN = Pattern.compile("(?is)COMMENT\\s+ON\\s+TABLE\\s+(.+?)\\s+IS\\s+'((?:''|[^'])*)'");
   private static final Pattern COMMENT_COLUMN_PATTERN = Pattern.compile("(?is)COMMENT\\s+ON\\s+COLUMN\\s+(.+?)\\.\"((?:\"\"|[^\"])*)\"\\s+IS\\s+'((?:''|[^'])*)'");

   private final DatasourceRegistry datasourceRegistry;
   private final JdbcExecutor executor;
   private final SchemaDiffEngine schemaDiffEngine;
   private final ConcurrentMap<String, String> activeSchemas;

   public GenericMcpTools(DatasourceRegistry datasourceRegistry, JdbcExecutor executor) {
      this.datasourceRegistry = datasourceRegistry;
      this.executor = executor;
      this.schemaDiffEngine = new SchemaDiffEngine();
      this.activeSchemas = new ConcurrentHashMap<>();
   }

   public List<RegisteredTool> getRegisteredTools() {
      List<RegisteredTool> tools = new ArrayList<>();
      tools.add(new RegisteredTool(this.tool("db_query", "执行只读 SELECT 或 WITH 查询。需要业务数据证据、统计结果或最终业务结论时，应优先使用此工具。", """
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
      tools.add(new RegisteredTool(this.tool("db_list_tables", "列出当前 schema 上下文中的候选表。仅用于发现和定位相关表，不代表已经查到业务数据，也不能直接作为最终结论依据。如果要获取业务数据，请调用 db_query。", """
         {
           "type": "object",
           "properties": {}
         }
         """), this.listTablesHandler()));
      tools.add(new RegisteredTool(this.tool("db_describe_table", "Describe table columns and metadata in the current schema context", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"}
           },
           "required": ["tableName"]
         }
         """), this.describeTableHandler()));
      tools.add(new RegisteredTool(this.tool("db_create_table", "Create a table from column definitions", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"},
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
             "ifExists": {"type": "boolean", "default": true},
             "cascade": {"type": "boolean", "default": false}
           },
           "required": ["tableName"]
         }
         """), this.dropTableHandler()));
      tools.add(new RegisteredTool(this.tool("db_get_ddl", "生成表 DDL 以检查表结构。仅用于核对列、约束等结构信息，不代表已经查到业务数据，也不能直接作为最终结论依据。如果要获取业务数据，请调用 db_query。", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"}
           },
           "required": ["tableName"]
         }
         """), this.getDdlHandler()));
      tools.add(new RegisteredTool(this.tool("db_list_indexes", "List indexes", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"}
           }
         }
         """), this.listIndexesHandler()));
      tools.add(new RegisteredTool(this.tool("db_create_index", "Create an index", """
         {
           "type": "object",
           "properties": {
             "indexName": {"type": "string"},
             "tableName": {"type": "string"},
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
             "ifExists": {"type": "boolean", "default": true}
           },
           "required": ["indexName"]
         }
         """), this.dropIndexHandler()));
      tools.add(new RegisteredTool(this.tool("db_analyze_index", "Analyze index usage", """
         {
           "type": "object",
           "properties": {
             "tableName": {"type": "string"}
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
         ObjectNode root = (ObjectNode) TOOL_SCHEMA_MAPPER.readTree(schema);
         ObjectNode properties = root.with("properties");
         properties.putObject("datasourceId").put("type", "string").put("description", "Configured datasource identifier");
         ArrayNode required = root.withArray("required");
         boolean present = false;
         for (int i = 0; i < required.size(); i++) {
            if ("datasourceId".equals(required.get(i).asText())) {
               present = true;
               break;
            }
         }
         if (!present) {
            required.add("datasourceId");
         }
         return TOOL_SCHEMA_MAPPER.convertValue(root, new TypeReference<>() {
         });
      } catch (JsonProcessingException ex) {
         throw new IllegalArgumentException("Invalid tool schema JSON", ex);
      }
   }

   private List<RegisteredTool> aliasTools(List<RegisteredTool> primaryTools) {
      List<RegisteredTool> aliases = new ArrayList<>();
      for (RegisteredTool primary : primaryTools) {
         String primaryName = primary.tool().name();
         if (primaryName.startsWith("db_")) {
            String aliasName = primaryName.equals("db_info") ? "pg_db_info" : "pg_" + primaryName.substring(3);
            aliases.add(new RegisteredTool(new McpSchema.Tool(aliasName, primary.tool().title(), primary.tool().description(), primary.tool().inputSchema(), primary.tool().outputSchema(), primary.tool().annotations(), primary.tool().meta()), primary.handler()));
         }
      }
      return aliases;
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> queryHandler() {
      return this.withDatasource((exchange, args, context) -> {
         String sql = (String) args.get("sql");
         Optional<String> validation = context.dialect().safetyPolicy().validateReadOnly(sql);
         return validation.map(ToolResults::error).orElseGet(() -> this.executeQuery(context, this.activeSchema(exchange, args, context), sql));
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> listSchemasHandler() {
      return this.withDatasource((exchange, args, context) -> this.executeQuery(context, this.activeSchema(exchange, args, context), context.dialect().sqlListSchemas()));
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> createSchemaHandler() {
      return this.withDatasource((exchange, args, context) -> {
         DatabaseDialect dialect = context.dialect();
         if (!dialect.capabilities().createSchema()) {
            return ToolResults.error("Schema creation is not supported for " + dialect.type());
         }

         String schema = (String) args.get("schema");
         if (!dialect.isSafeIdentifier(schema)) {
            return ToolResults.error("Invalid schema identifier");
         }

         boolean ifNotExists = (Boolean) args.getOrDefault("ifNotExists", true);
         return this.executeDdl(context, this.activeSchema(exchange, args, context), dialect.buildCreateSchemaSql(schema, ifNotExists), "Schema '" + schema + "' created successfully");
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> switchSchemaHandler() {
      return this.withDatasource((exchange, args, context) -> {
         DatabaseDialect dialect = context.dialect();
         if (!dialect.capabilities().switchSchema()) {
            return ToolResults.error("Schema switching is not supported for " + dialect.type());
         }

         String schema = (String) args.get("schema");
         if (!dialect.isSafeIdentifier(schema)) {
            return ToolResults.error("Invalid schema identifier");
         }

         this.activeSchemas.put(this.sessionDatasourceKey(exchange, context.config().id()), schema);
         return ToolResults.success("Active schema set to: " + schema);
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> listTablesHandler() {
      return this.withDatasource((exchange, args, context) -> {
         try (Connection conn = context.connectionManager().getConnection(this.activeSchema(exchange, args, context))) {
            return ToolResults.success(this.formatTableList(this.executor.query(conn, context.dialect().sqlListTables(), List.of())));
         } catch (SQLException ex) {
            return this.sqlError(ex);
         }
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> describeTableHandler() {
      return this.withDatasource((exchange, args, context) ->
         this.executePreparedQuery(context, this.activeSchema(exchange, args, context), context.dialect().sqlDescribeTable(), List.of(args.get("tableName")))
      );
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> createTableHandler() {
      return this.withDatasource((exchange, args, context) -> {
         DatabaseDialect dialect = context.dialect();
         String activeSchema = this.activeSchema(exchange, args, context);
         String tableName = (String) args.get("tableName");
         List<Map<String, Object>> columns = (List<Map<String, Object>>) args.get("columns");
         boolean ifNotExists = (Boolean) args.getOrDefault("ifNotExists", true);
         if (ifNotExists && this.objectExists(context, activeSchema, dialect.sqlTableExists(), List.of(this.normalizeIdentifierValue(dialect, tableName)))) {
            return ToolResults.success("Table '" + tableName + "' already exists");
         }
         return this.executeDdl(context, activeSchema, dialect.buildCreateTableSql(null, tableName, columns, ifNotExists), "Table '" + tableName + "' created successfully");
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> alterTableHandler() {
      return this.withDatasource((exchange, args, context) -> {
         String tableName = (String) args.get("tableName");
         String action = (String) args.get("action");
         return this.executeDdl(context, this.activeSchema(exchange, args, context), context.dialect().buildAlterTableSql(null, tableName, action, args), "Table '" + tableName + "' altered successfully");
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> dropTableHandler() {
      return this.withDatasource((exchange, args, context) -> {
         DatabaseDialect dialect = context.dialect();
         String activeSchema = this.activeSchema(exchange, args, context);
         String tableName = (String) args.get("tableName");
         boolean ifExists = (Boolean) args.getOrDefault("ifExists", true);
         boolean cascade = (Boolean) args.getOrDefault("cascade", false);
         if (ifExists && !this.objectExists(context, activeSchema, dialect.sqlTableExists(), List.of(this.normalizeIdentifierValue(dialect, tableName)))) {
            return ToolResults.success("Table '" + tableName + "' does not exist");
         }
         return this.executeDdl(context, activeSchema, dialect.buildDropTableSql(null, tableName, ifExists, cascade), "Table '" + tableName + "' dropped successfully");
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> getDdlHandler() {
      return this.withDatasource((exchange, args, context) -> {
         DatabaseDialect dialect = context.dialect();
         if (!dialect.capabilities().getDdl()) {
            return ToolResults.error("DDL generation is not implemented for " + dialect.type());
         }

         String tableName = (String) args.get("tableName");
         try (Connection conn = context.connectionManager().getConnection(this.activeSchema(exchange, args, context))) {
            return ToolResults.success(this.compactTableDdl(dialect.snapshotProvider().buildTableDdl(conn, null, tableName)));
         } catch (SQLException ex) {
            return ToolResults.error("Failed to generate DDL: " + ex.getMessage());
         }
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> listIndexesHandler() {
      return this.withDatasource((exchange, args, context) -> {
         String tableName = (String) args.get("tableName");
         boolean hasTableFilter = tableName != null && !tableName.isBlank();
         Optional<String> sql = context.dialect().sqlListIndexes(hasTableFilter);
         if (sql.isEmpty()) {
            return ToolResults.error("Index listing is not supported for " + context.dialect().type());
         }

         return hasTableFilter
            ? this.executePreparedQuery(context, this.activeSchema(exchange, args, context), sql.get(), List.of(tableName))
            : this.executePreparedQuery(context, this.activeSchema(exchange, args, context), sql.get(), List.of());
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> createIndexHandler() {
      return this.withDatasource((exchange, args, context) -> {
         DatabaseDialect dialect = context.dialect();
         String activeSchema = this.activeSchema(exchange, args, context);
         String tableName = (String) args.get("tableName");
         String indexName = (String) args.get("indexName");
         List<String> columns = (List<String>) args.get("columns");
         boolean unique = (Boolean) args.getOrDefault("unique", false);
         boolean ifNotExists = (Boolean) args.getOrDefault("ifNotExists", true);
         if (ifNotExists && this.objectExists(context, activeSchema, dialect.sqlIndexExists(), List.of(this.normalizeIdentifierValue(dialect, indexName)))) {
            return ToolResults.success("Index '" + indexName + "' already exists");
         }
         return this.executeDdl(context, activeSchema, dialect.buildCreateIndexSql(null, tableName, indexName, columns, unique, ifNotExists), "Index '" + indexName + "' created successfully");
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> dropIndexHandler() {
      return this.withDatasource((exchange, args, context) -> {
         DatabaseDialect dialect = context.dialect();
         String activeSchema = this.activeSchema(exchange, args, context);
         String indexName = (String) args.get("indexName");
         boolean ifExists = (Boolean) args.getOrDefault("ifExists", true);
         if (ifExists && !this.objectExists(context, activeSchema, dialect.sqlIndexExists(), List.of(this.normalizeIdentifierValue(dialect, indexName)))) {
            return ToolResults.success("Index '" + indexName + "' does not exist");
         }
         return this.executeDdl(context, activeSchema, dialect.buildDropIndexSql(null, indexName, ifExists), "Index '" + indexName + "' dropped successfully");
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> analyzeIndexHandler() {
      return this.withDatasource((exchange, args, context) -> {
         DatabaseDialect dialect = context.dialect();
         if (!dialect.capabilities().analyzeIndex()) {
            return ToolResults.error("Index analysis is not supported for " + dialect.type());
         }

         String tableName = (String) args.get("tableName");
         boolean hasTableFilter = tableName != null && !tableName.isBlank();
         Optional<String> sql = dialect.sqlAnalyzeIndexes(hasTableFilter);
         if (sql.isEmpty()) {
            return ToolResults.error("Index analysis SQL is not available for " + dialect.type());
         }

         return hasTableFilter
            ? this.executePreparedQuery(context, this.activeSchema(exchange, args, context), sql.get(), List.of(tableName))
            : this.executePreparedQuery(context, this.activeSchema(exchange, args, context), sql.get(), List.of());
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> executeHandler() {
      return this.withDatasource((exchange, args, context) -> {
         String sql = (String) args.get("sql");
         Optional<String> validation = context.dialect().safetyPolicy().validateExecute(sql);
         return validation.map(ToolResults::error).orElseGet(() -> this.executeSql(context, this.activeSchema(exchange, args, context), sql));
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> dbInfoHandler() {
      return this.withDatasource((exchange, args, context) -> this.executeQuery(context, this.activeSchema(exchange, args, context), context.dialect().sqlDbInfo()));
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> currentUserHandler() {
      return this.withDatasource((exchange, args, context) -> this.executeQuery(context, this.activeSchema(exchange, args, context), context.dialect().sqlCurrentUser()));
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> compareSchemasHandler() {
      return this.withDatasource((exchange, args, context) -> {
         DatabaseDialect dialect = context.dialect();
         if (!dialect.capabilities().compareSchemas() || dialect.type() != DatabaseType.POSTGRES) {
            return ToolResults.error("Schema compare is not implemented for " + dialect.type());
         }

         String sourceSchema = (String) args.get("sourceSchema");
         String targetSchema = (String) args.get("targetSchema");
         if (sourceSchema == null || sourceSchema.isBlank() || targetSchema == null || targetSchema.isBlank()) {
            return ToolResults.error("Both sourceSchema and targetSchema are required");
         }
         if (sourceSchema.equals(targetSchema)) {
            return ToolResults.error("sourceSchema and targetSchema must be different");
         }

         try (Connection conn = context.connectionManager().getConnection(this.activeSchema(exchange, args, context))) {
            SchemaSnapshot source = dialect.snapshotProvider().loadSnapshot(conn, sourceSchema);
            SchemaSnapshot target = dialect.snapshotProvider().loadSnapshot(conn, targetSchema);
            SchemaDiffResult result = this.schemaDiffEngine.compare(source, target);
            result.syncScripts().addAll(dialect.syncScriptGenerator().generateScripts(result));
            return ToolResults.success(this.formatCompareReport(result));
         } catch (SQLException ex) {
            return ToolResults.error("Schema compare failed: " + ex.getMessage());
         }
      });
   }

   private BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> withDatasource(DatasourceAwareHandler handler) {
      return (exchange, args) -> {
         try {
            DatasourceContext context = this.requiredDatasource(args);
            return handler.handle(exchange, args, context);
         } catch (IllegalArgumentException ex) {
            return ToolResults.error(ex.getMessage());
         }
      };
   }

   private DatasourceContext requiredDatasource(Map<String, Object> args) {
      Object datasourceId = args.get("datasourceId");
      if (!(datasourceId instanceof String value) || value.isBlank()) {
         throw new IllegalArgumentException("datasourceId is required");
      }
      return this.datasourceRegistry.getRequired(value);
   }

   private String activeSchema(McpSyncServerExchange exchange, Map<String, Object> args, DatasourceContext context) {
      return this.configuredSchema(exchange, args, context);
   }

   private String configuredSchema(McpSyncServerExchange exchange, Map<String, Object> args, DatasourceContext context) {
      Object schema = args.get("schema");
      if (schema instanceof String value && !value.isBlank()) {
         return value;
      }

      String activeSchema = this.activeSchemas.get(this.sessionDatasourceKey(exchange, context.config().id()));
      if (activeSchema != null && !activeSchema.isBlank()) {
         return activeSchema;
      }

      if (context.config().defaultSchema() != null && !context.config().defaultSchema().isBlank()) {
         return context.config().defaultSchema();
      }

      return null;
   }

   private String sessionDatasourceKey(McpSyncServerExchange exchange, String datasourceId) {
      String sessionId = exchange != null && exchange.sessionId() != null ? exchange.sessionId() : "default-session";
      return sessionId + "::" + datasourceId;
   }

   private McpSchema.CallToolResult executeQuery(DatasourceContext context, String activeSchema, String sql) {
      try (Connection conn = context.connectionManager().getConnection(activeSchema)) {
         return ToolResults.success(this.executor.toJson(this.executor.query(conn, sql)));
      } catch (SQLException ex) {
         return this.sqlError(ex);
      } catch (JsonProcessingException ex) {
         return ToolResults.error("Failed to serialize query result: " + ex.getMessage());
      }
   }

   private McpSchema.CallToolResult executePreparedQuery(DatasourceContext context, String activeSchema, String sql, List<Object> params) {
      try (Connection conn = context.connectionManager().getConnection(activeSchema)) {
         return ToolResults.success(this.executor.toJson(this.executor.query(conn, sql, params)));
      } catch (SQLException ex) {
         return this.sqlError(ex);
      } catch (JsonProcessingException ex) {
         return ToolResults.error("Failed to serialize query result: " + ex.getMessage());
      }
   }

   private McpSchema.CallToolResult executeDdl(DatasourceContext context, String activeSchema, String sql, String successMessage) {
      Optional<String> validation = context.dialect().safetyPolicy().validateExecute(sql);
      if (validation.isPresent()) {
         return ToolResults.error(validation.get());
      }

      try (Connection conn = context.connectionManager().getConnection(activeSchema)) {
         this.executor.execute(conn, sql);
         return ToolResults.success(successMessage);
      } catch (SQLException ex) {
         return this.sqlError(ex);
      }
   }

   private McpSchema.CallToolResult executeSql(DatasourceContext context, String activeSchema, String sql) {
      try (Connection conn = context.connectionManager().getConnection(activeSchema)) {
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

   private boolean objectExists(DatasourceContext context, String activeSchema, Optional<String> sqlOpt, List<Object> params) {
      if (sqlOpt.isEmpty()) {
         return false;
      }

      try (Connection conn = context.connectionManager().getConnection(activeSchema)) {
         return !this.executor.query(conn, sqlOpt.get(), params).isEmpty();
      } catch (SQLException ex) {
         return false;
      }
   }

   private String normalizeIdentifierValue(DatabaseDialect dialect, String value) {
      if (value == null) {
         return null;
      }
      return dialect.type() == DatabaseType.ORACLE ? value.toUpperCase() : value;
   }

   private String formatTableList(List<Map<String, Object>> rows) {
      StringBuilder result = new StringBuilder("table\tcols\tcomment");
      for (Map<String, Object> row : rows) {
         result.append("\n")
            .append(this.compactValue(this.firstValue(row, "table_name", "TABLE_NAME")))
            .append("\t")
            .append(this.compactValue(this.firstValue(row, "column_count", "COLUMN_COUNT")))
            .append("\t")
            .append(this.compactValue(this.firstValue(row, "table_comment", "TABLE_COMMENT")));
      }
      return result.toString();
   }

   private String compactTableDdl(String ddl) {
      Matcher create = CREATE_TABLE_PATTERN.matcher(ddl);
      if (!create.find()) {
         return ddl;
      }

      String tableName = this.compactIdentifierPath(create.group(1));
      String tableComment = "";
      Matcher tableCommentMatcher = COMMENT_TABLE_PATTERN.matcher(ddl);
      if (tableCommentMatcher.find()) {
         tableComment = this.unquoteSqlLiteral(tableCommentMatcher.group(2));
      }

      Map<String, String> columnComments = new LinkedHashMap<>();
      Matcher columnCommentMatcher = COMMENT_COLUMN_PATTERN.matcher(ddl);
      while (columnCommentMatcher.find()) {
         columnComments.put(this.unquoteIdentifier(columnCommentMatcher.group(2)), this.unquoteSqlLiteral(columnCommentMatcher.group(3)));
      }

      StringBuilder result = new StringBuilder();
      result.append("table\t").append(tableName);
      if (!tableComment.isBlank()) {
         result.append("\t").append(this.compactValue(tableComment));
      }
      result.append("\ncols\nname\ttype\tnull\tdefault\tcomment");

      for (String rawColumn : create.group(2).split("\\R")) {
         String column = rawColumn.trim();
         if (column.isBlank()) {
            continue;
         }
         if (column.endsWith(",")) {
            column = column.substring(0, column.length() - 1).trim();
         }
         ParsedColumn parsed = this.parseColumnDefinition(column);
         result.append("\n")
            .append(parsed.name())
            .append("\t")
            .append(parsed.type())
            .append("\t")
            .append(parsed.nullable() ? "" : "NN")
            .append("\t")
            .append(parsed.defaultValue())
            .append("\t")
            .append(this.compactValue(columnComments.get(parsed.name())));
      }
      return result.toString();
   }

   private ParsedColumn parseColumnDefinition(String column) {
      if (!column.startsWith("\"")) {
         return new ParsedColumn(column, "", true, "");
      }
      int end = this.findClosingQuote(column, 1);
      if (end < 0) {
         return new ParsedColumn(column, "", true, "");
      }

      String name = this.unquoteIdentifier(column.substring(1, end));
      String definition = column.substring(end + 1).trim();
      boolean nullable = !Pattern.compile("(?i)\\sNOT\\s+NULL(?:\\s|$)").matcher(" " + definition).find();
      String defaultValue = "";
      Matcher defaultMatcher = Pattern.compile("(?is)\\sDEFAULT\\s+(.+?)(?:\\s+NOT\\s+NULL\\s*$|\\s*$)").matcher(" " + definition);
      if (defaultMatcher.find()) {
         defaultValue = this.compactValue(defaultMatcher.group(1));
      }
      String type = definition
         .replaceFirst("(?is)\\s+DEFAULT\\s+.+?(?=\\s+NOT\\s+NULL\\s*$|\\s*$)", "")
         .replaceFirst("(?i)\\s+NOT\\s+NULL\\s*$", "")
         .trim();
      return new ParsedColumn(name, this.compactValue(type), nullable, defaultValue);
   }

   private int findClosingQuote(String value, int start) {
      for (int i = start; i < value.length(); i++) {
         if (value.charAt(i) == '"') {
            if (i + 1 < value.length() && value.charAt(i + 1) == '"') {
               i++;
            } else {
               return i;
            }
         }
      }
      return -1;
   }

   private Object firstValue(Map<String, Object> row, String... names) {
      for (String name : names) {
         if (row.containsKey(name)) {
            return row.get(name);
         }
      }
      return null;
   }

   private String compactIdentifierPath(String value) {
      StringBuilder result = new StringBuilder();
      Matcher matcher = Pattern.compile("\"((?:\"\"|[^\"])*)\"").matcher(value);
      while (matcher.find()) {
         if (!result.isEmpty()) {
            result.append(".");
         }
         result.append(this.unquoteIdentifier(matcher.group(1)));
      }
      return result.isEmpty() ? this.compactValue(value) : result.toString();
   }

   private String unquoteIdentifier(String value) {
      return value.replace("\"\"", "\"");
   }

   private String unquoteSqlLiteral(String value) {
      return value.replace("''", "'");
   }

   private String compactValue(Object value) {
      return value == null ? "" : value.toString().replaceAll("\\s+", " ").trim();
   }

   private record ParsedColumn(String name, String type, boolean nullable, String defaultValue) {
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

   @FunctionalInterface
   private interface DatasourceAwareHandler {
      McpSchema.CallToolResult handle(McpSyncServerExchange exchange, Map<String, Object> args, DatasourceContext context);
   }
}