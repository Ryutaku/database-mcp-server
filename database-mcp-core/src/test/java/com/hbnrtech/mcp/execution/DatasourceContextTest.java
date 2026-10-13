package com.hbnrtech.mcp.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.config.DatasourceConfig;
import com.hbnrtech.mcp.dialect.DatabaseDialect;
import com.hbnrtech.mcp.dialect.DatabaseDialectFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasourceContextTest {
   private final DatabaseDialect dialect = DatabaseDialectFactory.create(DatabaseType.ORACLE);

   @Test
   void usesTheDialectBlacklistWhenTheDatasourceConfiguresNone() {
      DatasourceContext context = this.context(null);

      // Oracle's built-in list blocks DROP USER and lets DROP TABLE through.
      assertFalse(context.safetyPolicy().validateExecute("drop user demo cascade").isEmpty());
      assertTrue(context.safetyPolicy().validateExecute("drop table demo").isEmpty());
   }

   @Test
   void usesTheRulesTheDatasourceConfigures() {
      DatasourceContext context = this.context(List.of("drop_table"));

      assertFalse(context.safetyPolicy().validateExecute("drop table demo").isEmpty());
      assertTrue(context.safetyPolicy().validateExecute("drop user demo cascade").isEmpty());
   }

   @Test
   void blocksNothingWhenTheDatasourceConfiguresAnEmptyBlacklist() {
      DatasourceContext context = this.context(List.of());

      assertTrue(context.safetyPolicy().validateExecute("drop database").isEmpty());
      assertTrue(context.safetyPolicy().validateExecute("drop user demo cascade").isEmpty());
   }

   private DatasourceContext context(List<String> ruleIds) {
      DatasourceConfig config = new DatasourceConfig("ds", DatabaseType.ORACLE, "jdbc:oracle:thin:@127.0.0.1:1521/FREEPDB1", "app", "secret", "APP", ruleIds);
      return new DatasourceContext(config, this.dialect, null);
   }
}
