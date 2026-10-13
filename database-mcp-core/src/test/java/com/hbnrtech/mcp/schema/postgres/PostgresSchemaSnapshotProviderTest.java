package com.hbnrtech.mcp.schema.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hbnrtech.mcp.schema.ColumnDef;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PostgresSchemaSnapshotProviderTest {
   private final PostgresSchemaSnapshotProvider provider = new PostgresSchemaSnapshotProvider();

   @Test
   void rendersTableDdlFromFetchedMetadata() {
      Map<String, ColumnDef> columns = new LinkedHashMap<>();
      columns.put("id", new ColumnDef("id", "bigint", false, "nextval('users_id_seq'::regclass)", 1));
      columns.put("name", new ColumnDef("name", "character varying", true, null, 2));

      String ddl = provider.renderTableDdl("app", "users", columns, "User accounts", Map.of("id", "Identifier"));

      assertEquals("""
         CREATE TABLE "app"."users" (
             "id" bigint NOT NULL DEFAULT nextval('users_id_seq'::regclass),
             "name" character varying
         );

         COMMENT ON TABLE "app"."users" IS 'User accounts';
         COMMENT ON COLUMN "app"."users"."id" IS 'Identifier';""", ddl);
   }

   @Test
   void rendersTableWithoutCommentsAsABareCreateStatement() {
      Map<String, ColumnDef> columns = new LinkedHashMap<>();
      columns.put("id", new ColumnDef("id", "integer", true, null, 1));

      String ddl = provider.renderTableDdl("public", "counters", columns, null, Map.of());

      assertEquals("""
         CREATE TABLE "public"."counters" (
             "id" integer
         )""", ddl);
   }
}
