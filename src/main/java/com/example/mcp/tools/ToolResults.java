package com.example.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;

public final class ToolResults {
   private ToolResults() {
   }

   public static McpSchema.CallToolResult success(String message) {
      return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), false);
   }

   public static McpSchema.CallToolResult error(String message) {
      return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), true);
   }
}
