package com.hbnrtech.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.hbnrtech.mcp.execution.DatasourceRegistry;
import com.hbnrtech.mcp.execution.JdbcExecutor;
import java.lang.reflect.Method;
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

   @Test
   void formatsListTablesAsCompactRows() throws Exception {
      GenericMcpTools tools = new GenericMcpTools(new DatasourceRegistry(Map.of()), mock(JdbcExecutor.class));
      Method method = GenericMcpTools.class.getDeclaredMethod("formatTableList", List.class);
      method.setAccessible(true);

      String result = (String) method.invoke(tools, List.of(Map.of("table_name", "T_USER", "column_count", 12, "table_comment", "User accounts")));

      assertEquals("table\tcols\tcomment\nT_USER\t12\tUser accounts", result);
   }

   @Test
   void compactsGeneratedDdlForModelContext() throws Exception {
      GenericMcpTools tools = new GenericMcpTools(new DatasourceRegistry(Map.of()), mock(JdbcExecutor.class));
      Method method = GenericMcpTools.class.getDeclaredMethod("compactTableDdl", String.class);
      method.setAccessible(true);

      String result = (String) method.invoke(tools, """
         CREATE TABLE "APP"."USERS" (
             "ID" NUMBER(18) NOT NULL,
             "NAME" VARCHAR2(80) DEFAULT 'anonymous'
         );

         COMMENT ON TABLE "APP"."USERS" IS 'User accounts';
         COMMENT ON COLUMN "APP"."USERS"."ID" IS 'Identifier';
         COMMENT ON COLUMN "APP"."USERS"."NAME" IS 'Display name';
         """);

      assertEquals("""
         table\tAPP.USERS\tUser accounts
         cols
         name\ttype\tnull\tdefault\tcomment
         ID\tNUMBER(18)\tNN\t\tIdentifier
         NAME\tVARCHAR2(80)\t\t'anonymous'\tDisplay name""", result);
   }

   private static RegisteredTool findTool(GenericMcpTools tools, String name) {
      List<RegisteredTool> registered = tools.getRegisteredTools();
      return registered.stream().filter(tool -> name.equals(tool.tool().name())).findFirst().orElseThrow();
   }

   private static boolean hasSchemaProperty(GenericMcpTools tools, String name) {
      return findTool(tools, name).tool().inputSchema().properties().get("schema") != null;
   }
}
