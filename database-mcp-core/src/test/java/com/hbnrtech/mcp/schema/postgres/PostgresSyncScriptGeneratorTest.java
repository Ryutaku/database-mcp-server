package com.hbnrtech.mcp.schema.postgres;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hbnrtech.mcp.schema.ColumnDiff;
import com.hbnrtech.mcp.schema.DiffType;
import com.hbnrtech.mcp.schema.IndexDiff;
import com.hbnrtech.mcp.schema.SchemaDiffResult;
import com.hbnrtech.mcp.schema.TableDiff;
import org.junit.jupiter.api.Test;

class PostgresSyncScriptGeneratorTest {
   private final PostgresSyncScriptGenerator generator = new PostgresSyncScriptGenerator();

   @Test
   void generatesTableAndIndexScripts() {
      SchemaDiffResult diff = new SchemaDiffResult("template_schema", "tenant_schema");
      TableDiff tableDiff = new TableDiff("users", DiffType.DIFFERENT, "CREATE TABLE template_schema.users (...)", "CREATE TABLE tenant_schema.users (...)");
      tableDiff.columnDiffs().add(new ColumnDiff("name", DiffType.MISSING_IN_TARGET, "varchar(255)", null, false, false));
      diff.tableDiffs().add(tableDiff);
      diff.indexDiffs().add(
         new IndexDiff("idx_users_name", DiffType.MISSING_IN_TARGET, "users", "CREATE INDEX idx_users_name ON template_schema.users (name)", null)
      );

      var scripts = generator.generateScripts(diff);

      assertTrue(scripts.stream().anyMatch(s -> s.contains("ALTER TABLE \"tenant_schema\".\"users\" ADD COLUMN \"name\" varchar(255);")));
      assertTrue(scripts.stream().anyMatch(s -> s.contains("CREATE INDEX idx_users_name ON tenant_schema.users (name);")));
   }
}
