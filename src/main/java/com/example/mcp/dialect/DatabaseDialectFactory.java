package com.example.mcp.dialect;

import com.example.mcp.config.DatabaseType;
import com.example.mcp.dialect.oracle.OracleDialect;
import com.example.mcp.dialect.postgres.PostgresDialect;

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
