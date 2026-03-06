package com.example.mcp.bootstrap;

import com.example.mcp.config.DatabaseConfig;
import com.example.mcp.dialect.DatabaseDialect;
import com.example.mcp.dialect.DatabaseDialectFactory;
import com.example.mcp.execution.ConnectionManager;
import com.example.mcp.execution.JdbcExecutor;
import com.example.mcp.tools.GenericMcpTools;
import com.example.mcp.tools.ToolRegistry;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

public class DatabaseMcpServer {
   public static void main(String[] args) {
      // 统一从环境变量读取数据库类型、连接串和默认 schema。
      DatabaseConfig config = DatabaseConfig.fromEnv();
      // 根据数据库类型创建对应方言，实现 PostgreSQL / Oracle 的行为隔离。
      DatabaseDialect dialect = DatabaseDialectFactory.create(config.type());
      System.err.println("Starting MCP Server...");
      System.err.println("Database Type: " + config.type());
      System.err.println("Database URL: " + config.url());
      System.err.println("Database User: " + config.username());
      McpJsonMapper jsonMapper = McpJsonMapper.createDefault();
      StdioServerTransportProvider transport = new StdioServerTransportProvider(jsonMapper);
      ConnectionManager connectionManager = new ConnectionManager(config, dialect);
      JdbcExecutor executor = new JdbcExecutor();
      GenericMcpTools tools = new GenericMcpTools(dialect, connectionManager, executor, config.defaultSchema());
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         // 关闭时显式释放连接池，避免客户端退出后连接长期残留。
         System.err.println("Shutting down MCP Server...");
         connectionManager.close();
         System.err.println("Connection pool closed.");
      }));
      var builder = McpServer.sync(transport)
         .jsonMapper(jsonMapper)
         .serverInfo(dialect.serverName(), "1.1.0");
      // 将所有工具一次性注册到 MCP Server。
      ToolRegistry.register(builder, tools);
      builder.build();
      System.err.println("MCP Server started successfully");
      System.err.println("Registered " + tools.getRegisteredTools().size() + " tools");

      try {
         // Stdio 模式下主线程保持阻塞，直到宿主进程主动结束。
         Thread.currentThread().join();
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
         System.err.println("Server interrupted: " + ex.getMessage());
      }
   }
}
