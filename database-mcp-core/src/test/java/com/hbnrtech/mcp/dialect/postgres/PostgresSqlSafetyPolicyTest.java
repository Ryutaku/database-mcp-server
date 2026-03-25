package com.hbnrtech.mcp.dialect.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PostgresSqlSafetyPolicyTest {
   private final PostgresSqlSafetyPolicy policy = new PostgresSqlSafetyPolicy();

   @Test
   void allowsSimpleReadOnlyQueries() {
      assertTrue(policy.validateReadOnly("select * from users").isEmpty());
      assertTrue(policy.validateReadOnly("with t as (select 1) select * from t").isEmpty());
   }

   @Test
   void blocksMutatingReadOnlyQueries() {
      assertFalse(policy.validateReadOnly("delete from users").isEmpty());
      assertFalse(policy.validateReadOnly("select * from users; delete from users").isEmpty());
      assertFalse(policy.validateReadOnly("select * from users where id in (select id from x); update x set a=1").isEmpty());
   }

   @Test
   void blocksDangerousExecuteStatements() {
      assertFalse(policy.validateExecute("DROP DATABASE demo").isEmpty());
      assertFalse(policy.validateExecute("ALTER SYSTEM SET work_mem='64MB'").isEmpty());
   }

   @Test
   void allowsRegularExecuteStatements() {
      assertTrue(policy.validateExecute("create table demo(id bigint)").isEmpty());
      assertTrue(policy.validateExecute("drop table demo").isEmpty());
   }
}
