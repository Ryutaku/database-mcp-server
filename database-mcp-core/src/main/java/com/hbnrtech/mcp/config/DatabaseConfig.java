package com.hbnrtech.mcp.config;

import java.util.Map;

public record DatabaseConfig(DatabaseType type, String url, String username, String password, String defaultSchema) {
   public static DatabaseConfig fromEnv() {
      return from(System.getenv());
   }

   public static DatabaseConfig from(Map<String, String> env) {
      // Unified DB_* variables take precedence, but keep PG_* fallback for existing clients.
      String dbType = env.get("DB_TYPE");
      String dbUrl = firstNonBlank(env.get("DB_URL"), env.get("PG_URL"));
      String dbUser = firstNonBlank(env.get("DB_USER"), env.get("PG_USER"));
      String dbPassword = firstNonBlank(env.get("DB_PASSWORD"), env.get("PG_PASSWORD"));
      String dbSchema = firstNonBlank(env.get("DB_SCHEMA"), env.get("PG_SCHEMA"));

      DatabaseType type = DatabaseType.from(dbType);
      String defaultUrl = type == DatabaseType.ORACLE ? "jdbc:oracle:thin:@localhost:1521/FREEPDB1" : "jdbc:postgresql://localhost:5432/postgres";
      String defaultUser = type == DatabaseType.ORACLE ? "system" : "postgres";
      String normalizedPassword = dbPassword == null ? "" : dbPassword;
      // PostgreSQL defaults to public; Oracle requires an explicit schema if needed.
      String normalizedSchema = dbSchema != null && !dbSchema.isBlank() ? dbSchema : type == DatabaseType.ORACLE ? null : "public";
      return new DatabaseConfig(type, firstNonBlank(dbUrl, defaultUrl), firstNonBlank(dbUser, defaultUser), normalizedPassword, normalizedSchema);
   }

   private static String firstNonBlank(String value, String fallback) {
      return value != null && !value.isBlank() ? value : fallback;
   }
}
