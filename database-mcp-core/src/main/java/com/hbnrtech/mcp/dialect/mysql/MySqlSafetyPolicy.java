package com.hbnrtech.mcp.dialect.mysql;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.execution.BaseSqlSafetyPolicy;
import com.hbnrtech.mcp.execution.ExecuteBlacklistRule;
import com.hbnrtech.mcp.execution.ExecuteBlacklistRules;
import java.util.List;

public class MySqlSafetyPolicy extends BaseSqlSafetyPolicy {
   @Override
   protected List<ExecuteBlacklistRule> executeBlacklist() {
      return ExecuteBlacklistRules.defaults(DatabaseType.MYSQL);
   }
}
