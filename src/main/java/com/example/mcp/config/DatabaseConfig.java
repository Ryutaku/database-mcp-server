package com.example.mcp.config;

import java.util.Map;

public record DatabaseConfig(DatabaseType type, String url, String username, String password, String defaultSchema) {
   public static DatabaseConfig fromEnv() {
      return from(System.getenv());
   }

   public static DatabaseConfig from(Map<String, String> env) {
      String dbType = env.get("DB_TYPE");
      String dbUrl = env.get("DB_URL");
      String dbUser = env.get("DB_USER");
      String dbPassword = env.get("DB_PASSWORD");
      String dbSchema = env.get("DB_SCHEMA");

      if ((dbType == null || dbType.isBlank()) && dbUrl == null && env.get("PG_URL") != null) {
         dbType = "postgres";
         dbUrl = env.get("PG_URL");
         dbUser = env.get("PG_USER");
         dbPassword = env.get("PG_PASSWORD");
      }

      DatabaseType type = DatabaseType.from(dbType);
      String defaultUrl = type == DatabaseType.ORACLE ? "jdbc:oracle:thin:@localhost:1521/FREEPDB1" : "jdbc:postgresql://localhost:5432/postgres";
      String defaultUser = type == DatabaseType.ORACLE ? "system" : "postgres";
      String normalizedPassword = dbPassword == null ? "" : dbPassword;
      String normalizedSchema = dbSchema != null && !dbSchema.isBlank() ? dbSchema : type == DatabaseType.ORACLE ? null : "public";
      return new DatabaseConfig(type, firstNonBlank(dbUrl, defaultUrl), firstNonBlank(dbUser, defaultUser), normalizedPassword, normalizedSchema);
   }

   private static String firstNonBlank(String value, String fallback) {
      return value != null && !value.isBlank() ? value : fallback;
   }
}
