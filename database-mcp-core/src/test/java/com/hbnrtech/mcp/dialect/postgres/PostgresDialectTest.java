package com.hbnrtech.mcp.dialect.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
      assertEquals("ALTER TABLE \"users\" ALTER COLUMN \"name\" TYPE varchar(255)", sql);
   }

   @Test
   void sizesIndexesThroughTheClassRowBecausePgIndexesHasNoIndexRelId() {
      String sql = dialect.sqlListIndexes(true).orElseThrow();

      // The old SQL asked for pg_relation_size(indexrelid). pg_indexes does not expose that column,
      // and neither does the pg_class row it joins to, so every db_list_indexes call with a
      // tableName failed with "column \"indexrelid\" does not exist".
      assertTrue(sql.contains("pg_relation_size(c.oid)"));
      assertFalse(sql.contains("indexrelid"));

      // Index names are unique per schema, so joining on relname alone can size an index of the
      // same name that lives in another schema.
      assertTrue(sql.contains("ns.nspname = i.schemaname"));
   }
}
