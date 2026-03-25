package com.hbnrtech.mcp.schema.oracle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hbnrtech.mcp.schema.ColumnDiff;
import com.hbnrtech.mcp.schema.DiffType;
import com.hbnrtech.mcp.schema.RoutineDiff;
import com.hbnrtech.mcp.schema.SchemaDiffResult;
import com.hbnrtech.mcp.schema.TableDiff;
import org.junit.jupiter.api.Test;

class OracleSyncScriptGeneratorTest {
   private final OracleSyncScriptGenerator generator = new OracleSyncScriptGenerator();

   @Test
   void generatesOracleSpecificAlterStatementsAndRoutineReviewNotes() {
      SchemaDiffResult diff = new SchemaDiffResult("SRC", "APP");
      TableDiff tableDiff = new TableDiff("USERS", DiffType.DIFFERENT, "CREATE TABLE SRC.USERS (...)", "CREATE TABLE APP.USERS (...)");
      tableDiff.columnDiffs().add(new ColumnDiff("NAME", DiffType.DIFFERENT, "VARCHAR2(255)", "VARCHAR2(100)", false, false));
      diff.tableDiffs().add(tableDiff);
      diff.routineDiffs().add(new RoutineDiff("SYNC_PROC", DiffType.DIFFERENT, "PROCEDURE SYNC_PROC", "PROCEDURE SYNC_PROC"));

      var scripts = generator.generateScripts(diff);

      assertTrue(scripts.stream().anyMatch(s -> s.contains("ALTER TABLE \"APP\".\"USERS\" MODIFY (\"NAME\" VARCHAR2(255));")));
      assertTrue(scripts.stream().anyMatch(s -> s.contains("review routine difference manually: SYNC_PROC")));
   }
}
