package com.hbnrtech.mcp.dialect.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OracleDialectTest {
   private final OracleDialect dialect = new OracleDialect();

   @Test
   void exposesOracleCapabilities() {
      assertFalse(dialect.capabilities().createSchema());
      assertFalse(dialect.capabilities().analyzeIndex());
      assertTrue(dialect.capabilities().getDdl());
      assertTrue(dialect.capabilities().compareSchemas());
   }

   @Test
   void normalizesOracleIdentifierRules() {
      assertTrue(dialect.isSafeIdentifier("DEMO_TABLE"));
      assertTrue(dialect.isSafeIdentifier("DEMO_TABLE$X"));
      assertFalse(dialect.isSafeIdentifier("bad-name"));
   }

   @Test
   void buildsOracleSpecificAlterTableSql() {
      String sql = dialect.buildAlterTableSql("APP", "USERS", "alter_column", java.util.Map.of("columnDef", java.util.Map.of("name", "NAME", "type", "VARCHAR2(255)")));
      assertEquals("ALTER TABLE \"APP\".\"USERS\" MODIFY (\"NAME\" VARCHAR2(255))", sql);
   }
}
