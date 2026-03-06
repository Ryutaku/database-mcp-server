package com.example.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.function.Function;

public record RegisteredTool(McpSchema.Tool tool, Function<Map<String, Object>, McpSchema.CallToolResult> handler) {
}
