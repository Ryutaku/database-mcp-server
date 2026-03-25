package com.hbnrtech.mcp.tools;

import io.modelcontextprotocol.server.McpServer;

public final class ToolRegistry {
   private ToolRegistry() {
   }

   public static void register(McpServer.SyncSpecification<?> builder, GenericMcpTools tools) {
      for (RegisteredTool registeredTool : tools.getRegisteredTools()) {
         builder.tool(registeredTool.tool(), registeredTool.handler()::apply);
      }
   }
}
