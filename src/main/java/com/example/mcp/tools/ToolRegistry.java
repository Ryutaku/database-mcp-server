package com.example.mcp.tools;

import io.modelcontextprotocol.server.McpServer;

public final class ToolRegistry {
   private ToolRegistry() {
   }

   public static void register(McpServer.SyncSpecification<?> builder, GenericMcpTools tools) {
      // MCP SDK 注册时需要把工具描述和执行函数一起挂到 builder 上。
      for (RegisteredTool registeredTool : tools.getRegisteredTools()) {
         builder.tool(registeredTool.tool(), (exchange, args) -> registeredTool.handler().apply(args));
      }
   }
}
