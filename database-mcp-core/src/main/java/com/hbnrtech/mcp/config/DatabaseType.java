package com.hbnrtech.mcp.config;

public enum DatabaseType {
   POSTGRES,
   ORACLE;

   public static DatabaseType from(String value) {
      if (value == null || value.isBlank()) {
         return POSTGRES;
      }

      return switch (value.trim().toLowerCase()) {
         case "postgres", "postgresql" -> POSTGRES;
         case "oracle" -> ORACLE;
         default -> throw new IllegalArgumentException("Unsupported DB_TYPE: " + value);
      };
   }
}
