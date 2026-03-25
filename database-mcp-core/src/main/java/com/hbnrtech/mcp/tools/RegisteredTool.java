package com.hbnrtech.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.function.BiFunction;

public record RegisteredTool(
   McpSchema.Tool tool,
   BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> handler
) {
}
