package com.example.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseConfigTest {
   @Test
   void usesUnifiedDatabaseConfigurationWhenPresent() {
      DatabaseConfig config = DatabaseConfig.from(
         Map.of(
            "DB_TYPE", "oracle",
            "DB_URL", "jdbc:oracle:thin:@dbhost:1521/FREEPDB1",
            "DB_USER", "system",
            "DB_PASSWORD", "secret",
            "DB_SCHEMA", "APP"
         )
      );

      assertEquals(DatabaseType.ORACLE, config.type());
      assertEquals("jdbc:oracle:thin:@dbhost:1521/FREEPDB1", config.url());
      assertEquals("system", config.username());
      assertEquals("secret", config.password());
      assertEquals("APP", config.defaultSchema());
   }

   @Test
   void fallsBackToLegacyPostgresVariables() {
      DatabaseConfig config = DatabaseConfig.from(
         Map.of(
            "PG_URL", "jdbc:postgresql://localhost:5432/app",
            "PG_USER", "app_user",
            "PG_PASSWORD", "pw"
         )
      );

      assertEquals(DatabaseType.POSTGRES, config.type());
      assertEquals("jdbc:postgresql://localhost:5432/app", config.url());
      assertEquals("app_user", config.username());
      assertEquals("pw", config.password());
      assertEquals("public", config.defaultSchema());
   }

   @Test
   void appliesEngineDefaultsWhenValuesAreMissing() {
      DatabaseConfig postgres = DatabaseConfig.from(Map.of());
      DatabaseConfig oracle = DatabaseConfig.from(Map.of("DB_TYPE", "oracle"));

      assertEquals(DatabaseType.POSTGRES, postgres.type());
      assertEquals("jdbc:postgresql://localhost:5432/postgres", postgres.url());
      assertEquals("postgres", postgres.username());
      assertEquals("public", postgres.defaultSchema());

      assertEquals(DatabaseType.ORACLE, oracle.type());
      assertEquals("jdbc:oracle:thin:@localhost:1521/FREEPDB1", oracle.url());
      assertEquals("system", oracle.username());
      assertNull(oracle.defaultSchema());
   }
}
