package com.hbnrtech.mcp.execution;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public abstract class BaseSqlSafetyPolicy implements SqlSafetyPolicy {
   @Override
   public Optional<String> validateReadOnly(String sql) {
      if (sql == null || sql.trim().isEmpty()) {
         return Optional.of("SQL statement must not be empty");
      }

      String normalized = normalizeSql(sql);
      String withoutTrailingSemicolon = normalized.replaceFirst(";\\s*$", "");
      if (withoutTrailingSemicolon.contains(";")) {
         return Optional.of("Read-only query tool does not allow multiple statements");
      }

      String lower = normalized.toLowerCase(Locale.ROOT);
      if (!(lower.startsWith("select") || lower.startsWith("with"))) {
         return Optional.of("Read-only query tool only accepts SELECT or WITH queries");
      }

      if (containsReadOnlyForbiddenKeyword(lower)) {
         return Optional.of("Read-only query tool only allows non-mutating SQL");
      }

      return Optional.empty();
   }

   @Override
   public Optional<String> validateExecute(String sql) {
      if (sql == null || sql.trim().isEmpty()) {
         return Optional.of("SQL statement must not be empty");
      }

      String upperSql = normalizeSql(sql).toUpperCase(Locale.ROOT);

      for (ExecuteBlacklistRule rule : this.executeBlacklist()) {
         String regex = "(.*\\b)?" + rule.toRegex() + "(\\b.*)?";
         if (upperSql.matches(regex)) {
            return Optional.of("Blocked dangerous operation: " + rule.description());
         }
      }

      return Optional.empty();
   }

   protected String normalizeSql(String sql) {
      return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*$", " ").replaceAll("\\s+", " ").trim();
   }

   protected boolean containsReadOnlyForbiddenKeyword(String lowerSql) {
      return lowerSql.matches(".*\\b(insert|update|delete|merge|create|alter|drop|truncate|grant|revoke|vacuum|analyze|comment|copy)\\b.*");
   }

   /**
    * The statements this policy refuses to execute.
    */
   protected abstract List<ExecuteBlacklistRule> executeBlacklist();
}
