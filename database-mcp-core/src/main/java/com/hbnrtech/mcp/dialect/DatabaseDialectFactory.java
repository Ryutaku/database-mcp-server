package com.hbnrtech.mcp.dialect;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.dialect.oracle.OracleDialect;
import com.hbnrtech.mcp.dialect.postgres.PostgresDialect;

public final class DatabaseDialectFactory {
   private DatabaseDialectFactory() {
   }

   public static DatabaseDialect create(DatabaseType type) {
      return switch (type) {
         case POSTGRES -> new PostgresDialect();
         case ORACLE -> new OracleDialect();
      };
   }
}
