package com.example.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.function.Function;

// 绑定 MCP Tool 定义和实际处理函数，便于统一注册与别名复用。
public record RegisteredTool(McpSchema.Tool tool, Function<Map<String, Object>, McpSchema.CallToolResult> handler) {
}
