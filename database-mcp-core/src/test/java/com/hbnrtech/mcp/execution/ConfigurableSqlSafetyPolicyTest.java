package com.hbnrtech.mcp.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.dialect.oracle.OracleSqlSafetyPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurableSqlSafetyPolicyTest {
   private final SqlSafetyPolicy dialectPolicy = new OracleSqlSafetyPolicy();

   @Test
   void appliesTheConfiguredRules() {
      SqlSafetyPolicy policy = this.policyWith("drop_table");

      assertFalse(policy.validateExecute("drop table demo").isEmpty());
      assertFalse(policy.validateExecute("DROP\n  TABLE   demo").isEmpty());
   }

   @Test
   void configuredRulesReplaceTheDialectDefaults() {
      SqlSafetyPolicy policy = this.policyWith("drop_table");

      // drop_user is a dialect default, but it is not in the configured list.
      assertTrue(policy.validateExecute("drop user demo cascade").isEmpty());
   }

   @Test
   void emptyBlacklistBlocksNothing() {
      SqlSafetyPolicy policy = new ConfigurableSqlSafetyPolicy(this.dialectPolicy, List.of());

      assertTrue(policy.validateExecute("drop database").isEmpty());
      assertTrue(policy.validateExecute("drop user demo cascade").isEmpty());
   }

   @Test
   void reportsTheRuleThatBlockedTheStatement() {
      SqlSafetyPolicy policy = this.policyWith("drop_table");

      assertEquals("Blocked dangerous operation: DROP TABLE", policy.validateExecute("drop table demo").orElseThrow());
   }

   @Test
   void keepsTheDialectReadOnlyContract() {
      SqlSafetyPolicy policy = this.policyWith("drop_table");

      assertTrue(policy.validateReadOnly("select * from dual").isEmpty());
      assertFalse(policy.validateReadOnly("delete from demo").isEmpty());
   }

   private SqlSafetyPolicy policyWith(String... ruleIds) {
      return new ConfigurableSqlSafetyPolicy(this.dialectPolicy, ExecuteBlacklistRules.resolve(DatabaseType.ORACLE, List.of(ruleIds)));
   }
}
