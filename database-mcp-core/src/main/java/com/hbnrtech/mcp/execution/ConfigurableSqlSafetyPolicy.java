package com.hbnrtech.mcp.execution;

import java.util.List;
import java.util.Optional;

/**
 * A dialect safety policy whose {@code db_execute} blacklist has been replaced by the rules a
 * datasource has been configured with. Read-only validation is delegated unchanged: the blacklist
 * governs {@code db_execute} only, and the read-only tool keeps its own, stricter contract.
 */
public final class ConfigurableSqlSafetyPolicy extends BaseSqlSafetyPolicy {
   private final SqlSafetyPolicy dialectPolicy;
   private final List<ExecuteBlacklistRule> blacklist;

   public ConfigurableSqlSafetyPolicy(SqlSafetyPolicy dialectPolicy, List<ExecuteBlacklistRule> blacklist) {
      this.dialectPolicy = dialectPolicy;
      this.blacklist = List.copyOf(blacklist);
   }

   public List<ExecuteBlacklistRule> blacklist() {
      return this.blacklist;
   }

   @Override
   public Optional<String> validateReadOnly(String sql) {
      return this.dialectPolicy.validateReadOnly(sql);
   }

   @Override
   protected List<ExecuteBlacklistRule> executeBlacklist() {
      return this.blacklist;
   }
}
