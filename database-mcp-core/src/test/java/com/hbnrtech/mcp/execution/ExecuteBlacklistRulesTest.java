package com.hbnrtech.mcp.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hbnrtech.mcp.config.DatabaseType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExecuteBlacklistRulesTest {
   @Test
   void everyDefaultRuleIsPartOfItsCatalog() {
      for (DatabaseType type : DatabaseType.values()) {
         Set<String> catalogIds = ExecuteBlacklistRules.knownRuleIds(type);
         for (String defaultId : ExecuteBlacklistRules.defaultRuleIds(type)) {
            assertTrue(catalogIds.contains(defaultId), type + " default rule is missing from the catalog: " + defaultId);
         }
      }
   }

   @Test
   void ruleIdsAreUniqueWithinEachCatalog() {
      for (DatabaseType type : DatabaseType.values()) {
         List<ExecuteBlacklistRule> rules = ExecuteBlacklistRules.forType(type);
         Set<String> ids = ExecuteBlacklistRules.knownRuleIds(type);
         assertEquals(rules.size(), ids.size(), type + " has duplicate rule ids");
      }
   }

   @Test
   void defaultsMatchTheStatementsTheServerRefusedBeforeTheBlacklistWasConfigurable() {
      assertEquals(
         List.of("drop_database", "drop_user", "drop_tablespace", "alter_system"),
         ExecuteBlacklistRules.defaultRuleIds(DatabaseType.ORACLE)
      );
      assertEquals(
         List.of("drop_database", "drop_schema", "drop_user", "drop_role", "drop_tablespace", "alter_system"),
         ExecuteBlacklistRules.defaultRuleIds(DatabaseType.POSTGRES)
      );
      assertEquals(
         List.of("drop_database", "drop_schema", "drop_user", "reset_master", "reset_replica", "shutdown"),
         ExecuteBlacklistRules.defaultRuleIds(DatabaseType.MYSQL)
      );
   }

   @Test
   void resolveKeepsCatalogOrderAndDropsUnknownIds() {
      List<ExecuteBlacklistRule> resolved = ExecuteBlacklistRules.resolve(
         DatabaseType.ORACLE,
         List.of("drop_table", "no_such_rule", "alter_system", "drop_table")
      );

      assertEquals(List.of("alter_system", "drop_table"), resolved.stream().map(ExecuteBlacklistRule::id).toList());
   }

   @Test
   void resolveFallsBackToDefaultsWhenNoRuleIdsAreConfigured() {
      assertEquals(
         ExecuteBlacklistRules.defaultRuleIds(DatabaseType.ORACLE),
         ExecuteBlacklistRules.resolve(DatabaseType.ORACLE, null).stream().map(ExecuteBlacklistRule::id).toList()
      );
   }

   @Test
   void phraseBecomesAWhitespaceTolerantPattern() {
      assertEquals("DROP\\s+TABLE", new ExecuteBlacklistRule("drop_table", "DROP TABLE", "删除表", "DROP TABLE").toRegex());
      assertEquals("SHUTDOWN", new ExecuteBlacklistRule("shutdown", "SHUTDOWN", "关闭实例", "SHUTDOWN").toRegex());
   }
}
