package com.example.mcp.dialect.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PostgresDialectTest {
   private final PostgresDialect dialect = new PostgresDialect();

   @Test
   void exposesPostgresCapabilities() {
      assertTrue(dialect.capabilities().createSchema());
      assertTrue(dialect.capabilities().analyzeIndex());
      assertTrue(dialect.capabilities().getDdl());
      assertTrue(dialect.capabilities().compareSchemas());
   }

   @Test
   void buildsPostgresSpecificAlterTableSql() {
      String sql = dialect.buildAlterTableSql("public", "users", "alter_column", java.util.Map.of("columnDef", java.util.Map.of("name", "name", "type", "varchar(255)")));
      assertEquals("ALTER TABLE \"public\".\"users\" ALTER COLUMN \"name\" TYPE varchar(255)", sql);
   }
}
