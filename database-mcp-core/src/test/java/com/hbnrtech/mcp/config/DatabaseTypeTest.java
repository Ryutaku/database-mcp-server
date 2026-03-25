package com.hbnrtech.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
   }

   @Test
   void rejectsUnsupportedDatabaseType() {
      assertThrows(IllegalArgumentException.class, () -> DatabaseType.from("mysql"));
   }
}
