package com.hbnrtech.mcp.execution;

import java.util.Locale;

/**
 * One {@code db_execute} blacklist entry.
 *
 * <p>Rules are authored in code as a per-database catalog ({@link ExecuteBlacklistRules}); a
 * datasource only stores the {@link #id()} values it has enabled. Nothing user supplied ever
 * reaches the regex engine, which is what keeps the blacklist safe to expose as a checklist.
 *
 * @param id          stable storage key, never renamed once shipped
 * @param phrase      SQL phrase, whitespace separated, e.g. {@code DROP TABLE}
 * @param label       human readable name shown in the admin console
 * @param description name used in the block message returned to the MCP client
 */
public record ExecuteBlacklistRule(String id, String phrase, String label, String description) {
   /**
    * Turns the phrase into the pattern shape {@code BaseSqlSafetyPolicy} matches against the
    * uppercased statement: whitespace between tokens becomes {@code \s+}, so {@code DROP  TABLE}
    * and {@code DROP\nTABLE} are caught the same way.
    */
   public String toRegex() {
      return String.join("\\s+", this.phrase.toUpperCase(Locale.ROOT).trim().split("\\s+"));
   }
}
