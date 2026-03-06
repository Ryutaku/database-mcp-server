package com.example.mcp.dialect.oracle;

import com.example.mcp.execution.BaseSqlSafetyPolicy;

public class OracleSqlSafetyPolicy extends BaseSqlSafetyPolicy {
   @Override
   protected String[] dangerousPatterns() {
      return new String[]{"DROP\\s+DATABASE", "DROP\\s+USER\\s+", "DROP\\s+TABLESPACE", "ALTER\\s+SYSTEM"};
   }

   @Override
   protected String[] dangerousDescriptions() {
      return new String[]{"DROP DATABASE", "DROP USER", "DROP TABLESPACE", "ALTER SYSTEM"};
   }
}
