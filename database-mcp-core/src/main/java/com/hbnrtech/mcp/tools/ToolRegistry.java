package com.hbnrtech.mcp.tools;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ToolRegistry {
   private ToolRegistry() {
   }

   public static void register(McpServer.SyncSpecification<?> builder, GenericMcpTools tools) {
      List<McpServerFeatures.SyncToolSpecification> specifications = new ArrayList<>();
      for (RegisteredTool registeredTool : tools.getRegisteredTools()) {
         specifications.add(new McpServerFeatures.SyncToolSpecification(
            registeredTool.tool(),
            (exchange, request) -> registeredTool.handler().apply(
               exchange,
               request.arguments() == null ? Map.of() : request.arguments()
            )
         ));
      }
      builder.tools(specifications);
   }
}
