package com.hbnrtech.mcp.tools;

/**
 * Receives every MCP tool call the server handles. The HTTP module persists these to build the
 * hotspot ranking; the stdio module has nowhere to put them and keeps {@link #NOOP}.
 *
 * <p>Called on the tool execution thread, so an implementation must not block. The HTTP
 * implementation only offers the record to a queue.
 */
public interface ToolInvocationListener {
   ToolInvocationListener NOOP = invocation -> {
   };

   void record(ToolInvocation invocation);
}
