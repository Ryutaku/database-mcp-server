package com.hbnrtech.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.hbnrtech.mcp.execution.DatasourceRegistry;
import com.hbnrtech.mcp.execution.JdbcExecutor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenericMcpToolsTest {

   @Test
   void omitsSchemaFromRoutineToolContracts() {
      GenericMcpTools tools = new GenericMcpTools(new DatasourceRegistry(Map.of()), mock(JdbcExecutor.class));

      assertFalse(hasSchemaProperty(tools, "db_list_tables"));
      assertFalse(hasSchemaProperty(tools, "db_describe_table"));
      assertFalse(hasSchemaProperty(tools, "db_create_table"));
      assertFalse(hasSchemaProperty(tools, "db_alter_table"));
      assertFalse(hasSchemaProperty(tools, "db_drop_table"));
      assertFalse(hasSchemaProperty(tools, "db_get_ddl"));
      assertFalse(hasSchemaProperty(tools, "db_list_indexes"));
      assertFalse(hasSchemaProperty(tools, "db_create_index"));
      assertFalse(hasSchemaProperty(tools, "db_drop_index"));
      assertFalse(hasSchemaProperty(tools, "db_analyze_index"));
   }

   @Test
   void keepsExplicitSchemaOnlyForCrossSchemaTools() {
      GenericMcpTools tools = new GenericMcpTools(new DatasourceRegistry(Map.of()), mock(JdbcExecutor.class));

      assertTrue(hasSchemaProperty(tools, "db_create_schema"));
      assertTrue(hasSchemaProperty(tools, "db_switch_schema"));
      assertNull(findTool(tools, "db_compare_schemas").tool().inputSchema().properties().get("schema"));
   }

   @Test
   void aliasContractsMatchTheSchemaFreePrimaryTools() {
      GenericMcpTools tools = new GenericMcpTools(new DatasourceRegistry(Map.of()), mock(JdbcExecutor.class));

      assertFalse(hasSchemaProperty(tools, "pg_list_tables"));
      assertFalse(hasSchemaProperty(tools, "pg_describe_table"));
      assertFalse(hasSchemaProperty(tools, "pg_list_indexes"));
      assertFalse(hasSchemaProperty(tools, "pg_create_index"));
   }

   private static RegisteredTool findTool(GenericMcpTools tools, String name) {
      List<RegisteredTool> registered = tools.getRegisteredTools();
      return registered.stream().filter(tool -> name.equals(tool.tool().name())).findFirst().orElseThrow();
   }

   private static boolean hasSchemaProperty(GenericMcpTools tools, String name) {
      return findTool(tools, name).tool().inputSchema().properties().get("schema") != null;
   }
}
