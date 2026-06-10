package com.hbnrtech.mcp.config;

public enum DatabaseType {
   POSTGRES,
   ORACLE,
   MYSQL;

   public static DatabaseType from(String value) {
      if (value == null || value.isBlank()) {
         return POSTGRES;
      }

      return switch (value.trim().toLowerCase()) {
         case "postgres", "postgresql" -> POSTGRES;
         case "oracle" -> ORACLE;
         case "mysql" -> MYSQL;
         default -> throw new IllegalArgumentException("Unsupported DB_TYPE: " + value);
      };
   }
}
