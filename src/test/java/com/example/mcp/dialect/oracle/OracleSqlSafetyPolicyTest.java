package com.example.mcp.dialect.oracle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OracleSqlSafetyPolicyTest {
   private final OracleSqlSafetyPolicy policy = new OracleSqlSafetyPolicy();

   @Test
   void allowsSimpleReadOnlyQueries() {
      assertTrue(policy.validateReadOnly("select * from dual").isEmpty());
   }

   @Test
   void blocksDangerousExecuteStatements() {
      assertFalse(policy.validateExecute("drop user demo cascade").isEmpty());
      assertFalse(policy.validateExecute("alter system switch logfile").isEmpty());
   }

   @Test
   void allowsRegularExecuteStatements() {
      assertTrue(policy.validateExecute("create table demo(id number)").isEmpty());
      assertTrue(policy.validateExecute("drop table demo").isEmpty());
   }
}
