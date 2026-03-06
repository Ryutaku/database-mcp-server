package com.example.mcp.dialect.postgres;

import com.example.mcp.execution.BaseSqlSafetyPolicy;

public class PostgresSqlSafetyPolicy extends BaseSqlSafetyPolicy {
   @Override
   protected String[] dangerousPatterns() {
      return new String[]{"DROP\\s+DATABASE", "DROP\\s+SCHEMA\\s+", "DROP\\s+USER\\s+", "DROP\\s+ROLE\\s+", "DROP\\s+TABLESPACE", "ALTER\\s+SYSTEM"};
   }

   @Override
   protected String[] dangerousDescriptions() {
      return new String[]{"DROP DATABASE", "DROP SCHEMA", "DROP USER", "DROP ROLE", "DROP TABLESPACE", "ALTER SYSTEM"};
   }
}
