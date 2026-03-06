package com.example.mcp.config;

import java.util.Map;

public record DatabaseConfig(DatabaseType type, String url, String username, String password, String defaultSchema) {
   public static DatabaseConfig fromEnv() {
      return from(System.getenv());
   }

   public static DatabaseConfig from(Map<String, String> env) {
      // 优先读取统一命名的 DB_* 变量，便于不同数据库复用同一套启动方式。
      String dbType = env.get("DB_TYPE");
      String dbUrl = env.get("DB_URL");
      String dbUser = env.get("DB_USER");
      String dbPassword = env.get("DB_PASSWORD");
      String dbSchema = env.get("DB_SCHEMA");

      DatabaseType type = DatabaseType.from(dbType);
      String defaultUrl = type == DatabaseType.ORACLE ? "jdbc:oracle:thin:@localhost:1521/FREEPDB1" : "jdbc:postgresql://localhost:5432/postgres";
      String defaultUser = type == DatabaseType.ORACLE ? "system" : "postgres";
      String normalizedPassword = dbPassword == null ? "" : dbPassword;
      // PostgreSQL 默认使用 public，Oracle 交给调用方显式指定 schema 更安全。
      String normalizedSchema = dbSchema != null && !dbSchema.isBlank() ? dbSchema : type == DatabaseType.ORACLE ? null : "public";
      return new DatabaseConfig(type, firstNonBlank(dbUrl, defaultUrl), firstNonBlank(dbUser, defaultUser), normalizedPassword, normalizedSchema);
   }

   private static String firstNonBlank(String value, String fallback) {
      return value != null && !value.isBlank() ? value : fallback;
   }
}
