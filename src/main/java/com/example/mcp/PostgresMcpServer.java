package com.example.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransport;

public class PostgresMcpServer {
   public static void main(String[] args) {
      String dbUrl = System.getenv().getOrDefault("PG_URL", "jdbc:postgresql://localhost:5432/postgres");
      String dbUser = System.getenv().getOrDefault("PG_USER", "postgres");
      String dbPassword = System.getenv().getOrDefault("PG_PASSWORD", "");
      System.err.println("Starting PostgreSQL MCP Server...");
      System.err.println("Database URL: " + dbUrl);
      System.err.println("Database User: " + dbUser);
      ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      StdioServerTransport transport = new StdioServerTransport(objectMapper);
      PgsqlTools pgsqlTools = new PgsqlTools(dbUrl, dbUser, dbPassword);
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         System.err.println("Shutting down PostgreSQL MCP Server...");
         pgsqlTools.close();
         System.err.println("Connection pool closed.");
      }));
      McpServer.sync(transport)
         .serverInfo("postgres-mcp-server", "1.0.0")
         .tool(pgsqlTools.getQueryTool(), pgsqlTools.getQueryHandler())
         .tool(pgsqlTools.getListSchemasTool(), pgsqlTools.getListSchemasHandler())
         .tool(pgsqlTools.getCreateSchemaTool(), pgsqlTools.getCreateSchemaHandler())
         .tool(pgsqlTools.getSwitchSchemaTool(), pgsqlTools.getSwitchSchemaHandler())
         .tool(pgsqlTools.getListTablesTool(), pgsqlTools.getListTablesHandler())
         .tool(pgsqlTools.getDescribeTableTool(), pgsqlTools.getDescribeTableHandler())
         .tool(pgsqlTools.getCreateTableTool(), pgsqlTools.getCreateTableHandler())
         .tool(pgsqlTools.getAlterTableTool(), pgsqlTools.getAlterTableHandler())
         .tool(pgsqlTools.getDropTableTool(), pgsqlTools.getDropTableHandler())
         .tool(pgsqlTools.getGetDDLTool(), pgsqlTools.getGetDDLHandler())
         .tool(pgsqlTools.getListIndexesTool(), pgsqlTools.getListIndexesHandler())
         .tool(pgsqlTools.getCreateIndexTool(), pgsqlTools.getCreateIndexHandler())
         .tool(pgsqlTools.getDropIndexTool(), pgsqlTools.getDropIndexHandler())
         .tool(pgsqlTools.getAnalyzeIndexTool(), pgsqlTools.getAnalyzeIndexHandler())
         .tool(pgsqlTools.getExecuteTool(), pgsqlTools.getExecuteHandler())
         .tool(pgsqlTools.getDbInfoTool(), pgsqlTools.getDbInfoHandler())
         .tool(pgsqlTools.getCurrentUserTool(), pgsqlTools.getCurrentUserHandler())
         .tool(pgsqlTools.getCompareSchemaTool(), pgsqlTools.getCompareSchemaHandler())
         .build();
      System.err.println("PostgreSQL MCP Server started successfully");
      System.err.println("Registered 18 tools:");
      System.err.println("  - pg_query (SELECT查询)");
      System.err.println("  - pg_list_schemas (列出Schema)");
      System.err.println("  - pg_create_schema (创建Schema)");
      System.err.println("  - pg_switch_schema (切换Schema)");
      System.err.println("  - pg_list_tables (列出表)");
      System.err.println("  - pg_describe_table (表结构)");
      System.err.println("  - pg_create_table (建表)");
      System.err.println("  - pg_alter_table (改表)");
      System.err.println("  - pg_drop_table (删表)");
      System.err.println("  - pg_get_ddl (获取DDL)");
      System.err.println("  - pg_list_indexes (列出索引)");
      System.err.println("  - pg_create_index (创建索引)");
      System.err.println("  - pg_drop_index (删除索引)");
      System.err.println("  - pg_analyze_index (分析索引)");
      System.err.println("  - pg_execute (执行SQL)");
      System.err.println("  - pg_db_info (数据库信息)");
      System.err.println("  - pg_current_user (当前用户信息)");
      System.err.println("  - pg_compare_schemas (比较Schema差异)");

      try {
         Thread.currentThread().join();
      } catch (InterruptedException var8) {
         Thread.currentThread().interrupt();
         System.err.println("Server interrupted: " + var8.getMessage());
      }
   }
}
