package com.example.mcp.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaDiffEngineTest {
   private final SchemaDiffEngine engine = new SchemaDiffEngine();

   @Test
   void reportsNoDifferencesForEquivalentSnapshots() {
      SchemaSnapshot source = snapshotWithSingleTable("public", column("id", "bigint", false, null, 1));
      SchemaSnapshot target = snapshotWithSingleTable("tenant", column("id", "bigint", false, null, 1));

      SchemaDiffResult result = engine.compare(source, target);

      assertFalse(result.hasDifferences());
      assertEquals(0, result.tableDiffs().size());
      assertEquals(0, result.indexDiffs().size());
   }

   @Test
   void reportsMissingAndChangedColumns() {
      SchemaSnapshot source = snapshotWithSingleTable(
         "source",
         column("id", "bigint", false, null, 1),
         column("name", "varchar(50)", true, null, 2)
      );
      SchemaSnapshot target = snapshotWithSingleTable(
         "target",
         column("id", "integer", false, null, 1),
         column("extra_col", "text", true, null, 2)
      );

      SchemaDiffResult result = engine.compare(source, target);

      assertTrue(result.hasDifferences());
      assertEquals(1, result.tableDiffs().size());
      assertEquals(3, result.tableDiffs().getFirst().columnDiffs().size());
   }

   private static SchemaSnapshot snapshotWithSingleTable(String schema, ColumnDef... columns) {
      Map<String, ColumnDef> columnMap = new LinkedHashMap<>();
      for (ColumnDef column : columns) {
         columnMap.put(column.name(), column);
      }

      TableDef table = new TableDef("users", schema, "CREATE TABLE " + schema + ".users (...)", columnMap);
      Map<String, TableDef> tables = new LinkedHashMap<>();
      tables.put("users", table);
      return new SchemaSnapshot(schema, tables, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
   }

   private static ColumnDef column(String name, String type, boolean nullable, String defaultValue, int ordinal) {
      return new ColumnDef(name, type, nullable, defaultValue, ordinal);
   }
}
