package com.hbnrtech.mcp.tools;

/**
 * One MCP tool call against one datasource, as observed by the recording decorator.
 *
 * <p>The SQL text is deliberately not captured. The ranking only needs the datasource, the tool,
 * the outcome and the elapsed time, and keeping statements out of the store avoids both a growth
 * problem and a data-handling problem.
 *
 * @param datasourceId the id the caller asked for, or {@link #UNKNOWN_DATASOURCE} when the argument
 *                     was missing or not a string
 * @param occurredAt   fixed-width ISO-8601 UTC timestamp, so a plain string comparison in the
 *                     store orders the same way the timeline does
 */
public record ToolInvocation(
   String datasourceId,
   String toolName,
   boolean success,
   long durationMillis,
   String errorMessage,
   String sessionId,
   String occurredAt
) {
   /** Placeholder for calls that never resolved a datasource, so they still show up in the ranking. */
   public static final String UNKNOWN_DATASOURCE = "<unknown>";

   /** Calls are counted per datasource, so an unresolvable id must not collapse into a null group. */
   public String datasourceIdOrUnknown() {
      return this.datasourceId == null || this.datasourceId.isBlank() ? UNKNOWN_DATASOURCE : this.datasourceId;
   }
}
