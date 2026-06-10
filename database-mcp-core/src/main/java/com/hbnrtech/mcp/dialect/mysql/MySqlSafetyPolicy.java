package com.hbnrtech.mcp.dialect.mysql;

import com.hbnrtech.mcp.execution.BaseSqlSafetyPolicy;

public class MySqlSafetyPolicy extends BaseSqlSafetyPolicy {
   @Override
   protected String[] dangerousPatterns() {
      return new String[]{"DROP\\s+DATABASE", "DROP\\s+SCHEMA\\s+", "DROP\\s+USER\\s+", "RESET\\s+MASTER", "RESET\\s+REPLICA", "SHUTDOWN"};
   }

   @Override
   protected String[] dangerousDescriptions() {
      return new String[]{"DROP DATABASE", "DROP SCHEMA", "DROP USER", "RESET MASTER", "RESET REPLICA", "SHUTDOWN"};
   }
}
