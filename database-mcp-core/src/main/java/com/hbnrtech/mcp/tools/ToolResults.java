package com.hbnrtech.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;

public final class ToolResults {
   private ToolResults() {
   }

   public static McpSchema.CallToolResult success(String message) {
      return new McpSchema.CallToolResult(message, false);
   }

   public static McpSchema.CallToolResult error(String message) {
      return new McpSchema.CallToolResult(message, true);
   }
}
