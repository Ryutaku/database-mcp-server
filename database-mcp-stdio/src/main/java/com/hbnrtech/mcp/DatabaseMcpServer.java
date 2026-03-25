package com.hbnrtech.mcp;

import com.hbnrtech.mcp.bootstrap.DatabaseMcpRuntime;
import com.hbnrtech.mcp.bootstrap.DatabaseMcpRuntimeFactory;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

public final class DatabaseMcpServer {
   private DatabaseMcpServer() {
   }

   public static void main(String[] args) {
      DatabaseMcpRuntime runtime = DatabaseMcpRuntimeFactory.createRuntime();
      System.err.println("Starting MCP Stdio Server...");
      System.err.println("Configured datasources: " + runtime.datasourceRegistry().datasources().keySet());

      McpJsonMapper jsonMapper = McpJsonMapper.createDefault();
      StdioServerTransportProvider transport = new StdioServerTransportProvider(jsonMapper);

      Runtime.getRuntime().addShutdownHook(new Thread(() -> DatabaseMcpRuntimeFactory.shutdown("MCP Stdio Server", runtime)));

      var builder = McpServer.sync(transport);
      DatabaseMcpRuntimeFactory.configureServer(builder, jsonMapper, runtime);
      builder.build();

      System.err.println("MCP Stdio Server started successfully");
      System.err.println("Registered " + runtime.tools().getRegisteredTools().size() + " tools");

      try {
         Thread.currentThread().join();
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
         System.err.println("Server interrupted: " + ex.getMessage());
      }
   }
}
