package com.hbnrtech.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hbnrtech.mcp.dialect.mysql.MySqlDialect;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseTypeTest {
   @Test
   void defaultsToPostgresWhenValueIsNullOrBlank() {
      assertEquals(DatabaseType.POSTGRES, DatabaseType.from(null));
      assertEquals(DatabaseType.POSTGRES, DatabaseType.from(""));
      assertEquals(DatabaseType.POSTGRES, DatabaseType.from("   "));
   }

   @Test
   void parsesSupportedAliases() {
      assertEquals(DatabaseType.POSTGRES, DatabaseType.from("postgres"));
      assertEquals(DatabaseType.POSTGRES, DatabaseType.from("postgresql"));
      assertEquals(DatabaseType.ORACLE, DatabaseType.from("oracle"));
      assertEquals(DatabaseType.MYSQL, DatabaseType.from("mysql"));
   }

   @Test
   void rejectsUnsupportedDatabaseType() {
      assertThrows(IllegalArgumentException.class, () -> DatabaseType.from("sqlserver"));
   }

   @Test
   void mysqlDialectExposesExpectedBehavior() {
      MySqlDialect dialect = new MySqlDialect();

      assertTrue(dialect.capabilities().createSchema());
      assertTrue(dialect.capabilities().switchSchema());
      assertTrue(dialect.capabilities().getDdl());
      assertFalse(dialect.capabilities().analyzeIndex());
      assertFalse(dialect.capabilities().compareSchemas());
      assertTrue(dialect.isSafeIdentifier("demo_table"));
      assertFalse(dialect.isSafeIdentifier("bad-name"));
      assertEquals(
         "ALTER TABLE `users` MODIFY COLUMN `name` varchar(255)",
         dialect.buildAlterTableSql("app", "users", "alter_column", Map.of("columnDef", Map.of("name", "name", "type", "varchar(255)")))
      );
      assertThrows(IllegalArgumentException.class, () -> dialect.buildDropIndexSql(null, null, "idx_users_name", true));
      assertEquals("DROP INDEX `idx_users_name` ON `users`", dialect.buildDropIndexSql(null, "users", "idx_users_name", true));
      assertTrue(dialect.sqlListTables().contains("t.table_comment"));
      assertTrue(dialect.sqlListTables().contains("DATABASE()"));
   }
}
