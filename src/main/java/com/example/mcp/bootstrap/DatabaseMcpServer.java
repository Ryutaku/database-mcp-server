package com.example.mcp.bootstrap;

import com.example.mcp.config.DatabaseConfig;
import com.example.mcp.dialect.DatabaseDialect;
import com.example.mcp.dialect.DatabaseDialectFactory;
import com.example.mcp.execution.ConnectionManager;
import com.example.mcp.execution.JdbcExecutor;
import com.example.mcp.tools.GenericMcpTools;
import com.example.mcp.tools.ToolRegistry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransport;

public class DatabaseMcpServer {
   public static void main(String[] args) {
      DatabaseConfig config = DatabaseConfig.fromEnv();
      DatabaseDialect dialect = DatabaseDialectFactory.create(config.type());
      System.err.println("Starting MCP Server...");
      System.err.println("Database Type: " + config.type());
      System.err.println("Database URL: " + config.url());
      System.err.println("Database User: " + config.username());
      ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      StdioServerTransport transport = new StdioServerTransport(objectMapper);
      ConnectionManager connectionManager = new ConnectionManager(config, dialect);
      JdbcExecutor executor = new JdbcExecutor();
      GenericMcpTools tools = new GenericMcpTools(dialect, connectionManager, executor, config.defaultSchema());
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         System.err.println("Shutting down MCP Server...");
         connectionManager.close();
         System.err.println("Connection pool closed.");
      }));
      var builder = McpServer.sync(transport).serverInfo(dialect.serverName(), "1.1.0");
      ToolRegistry.register(builder, tools);
      builder.build();
      System.err.println("MCP Server started successfully");
      System.err.println("Registered " + tools.getRegisteredTools().size() + " tools");

      try {
         Thread.currentThread().join();
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
         System.err.println("Server interrupted: " + ex.getMessage());
      }
   }
}
