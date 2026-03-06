package com.example.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;

public final class ToolResults {
   private ToolResults() {
   }

   public static McpSchema.CallToolResult success(String message) {
      // 第二个参数为 false，表示 MCP 工具执行成功。
      return new McpSchema.CallToolResult(message, false);
   }

   public static McpSchema.CallToolResult error(String message) {
      // 第二个参数为 true，表示 MCP 工具执行失败。
      return new McpSchema.CallToolResult(message, true);
   }
}
