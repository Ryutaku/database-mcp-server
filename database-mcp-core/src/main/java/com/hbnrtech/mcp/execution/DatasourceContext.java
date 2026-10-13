package com.hbnrtech.mcp.execution;

import com.hbnrtech.mcp.config.DatasourceConfig;
import com.hbnrtech.mcp.dialect.DatabaseDialect;
import java.util.List;

public record DatasourceContext(
   DatasourceConfig config,
   DatabaseDialect dialect,
   ConnectionManager connectionManager,
   SqlSafetyPolicy safetyPolicy
) {
   /**
    * Resolves the policy for this datasource. The dialect supplies the read-only contract and the
    * default blacklist; a datasource that carries its own rule ids gets those rules instead.
    */
   public DatasourceContext(DatasourceConfig config, DatabaseDialect dialect, ConnectionManager connectionManager) {
      this(config, dialect, connectionManager, resolveSafetyPolicy(config, dialect));
   }

   private static SqlSafetyPolicy resolveSafetyPolicy(DatasourceConfig config, DatabaseDialect dialect) {
      List<String> ruleIds = config.executeBlacklistRuleIds();
      if (ruleIds == null) {
         return dialect.safetyPolicy();
      }
      return new ConfigurableSqlSafetyPolicy(dialect.safetyPolicy(), ExecuteBlacklistRules.resolve(config.type(), ruleIds));
   }
}
