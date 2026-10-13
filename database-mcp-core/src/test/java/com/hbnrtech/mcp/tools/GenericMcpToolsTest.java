package com.hbnrtech.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.config.DatasourceConfig;
import com.hbnrtech.mcp.dialect.mysql.MySqlDialect;
import com.hbnrtech.mcp.dialect.oracle.OracleDialect;
import com.hbnrtech.mcp.execution.ConnectionManager;
import com.hbnrtech.mcp.execution.DatasourceContext;
import com.hbnrtech.mcp.execution.DatasourceRegistry;
import com.hbnrtech.mcp.execution.JdbcExecutor;
import io.modelcontextprotocol.spec.McpSchema;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
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
      assertNull(schemaProperties(findTool(tools, "db_compare_schemas")).get("schema"));
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

   @Test
   void runsSchemaCompareForOracleDialect() throws SQLException {
      ConnectionManager connectionManager = mock(ConnectionManager.class);
      when(connectionManager.getConnection(any())).thenThrow(new SQLException("no database reachable"));
      GenericMcpTools tools = new GenericMcpTools(
         new DatasourceRegistry(Map.of("ora", datasourceContext(DatabaseType.ORACLE, new OracleDialect(), connectionManager))),
         mock(JdbcExecutor.class)
      );

      String message = callCompareSchemas(tools, Map.of("datasourceId", "ora", "sourceSchema", "SRC", "targetSchema", "APP"));

      assertTrue(message.contains("Schema compare failed"));
      assertFalse(message.contains("not implemented"));
   }

   @Test
   void rejectsOracleCompareWhenBothNamesResolveToTheSameOwner() throws SQLException {
      ConnectionManager connectionManager = mock(ConnectionManager.class);
      GenericMcpTools tools = new GenericMcpTools(
         new DatasourceRegistry(Map.of("ora", datasourceContext(DatabaseType.ORACLE, new OracleDialect(), connectionManager))),
         mock(JdbcExecutor.class)
      );

      String message = callCompareSchemas(tools, Map.of("datasourceId", "ora", "sourceSchema", "app", "targetSchema", "APP"));

      assertTrue(message.contains("must be different"));
      verify(connectionManager, never()).getConnection(any());
   }

   @Test
   void keepsSchemaCompareUnsupportedForDialectsWithoutTheCapability() throws SQLException {
      ConnectionManager connectionManager = mock(ConnectionManager.class);
      GenericMcpTools tools = new GenericMcpTools(
         new DatasourceRegistry(Map.of("my", datasourceContext(DatabaseType.MYSQL, new MySqlDialect(), connectionManager))),
         mock(JdbcExecutor.class)
      );

      String message = callCompareSchemas(tools, Map.of("datasourceId", "my", "sourceSchema", "src", "targetSchema", "app"));

      assertTrue(message.contains("not implemented"));
      verify(connectionManager, never()).getConnection(any());
   }

   @Test
   void recordsEveryCallWithItsDatasourceToolAndOutcome() {
      List<ToolInvocation> recorded = new ArrayList<>();
      GenericMcpTools tools = new GenericMcpTools(new DatasourceRegistry(Map.of()), mock(JdbcExecutor.class), recorded::add);

      findTool(tools, "db_query").handler().apply(null, Map.of("datasourceId", "not-configured", "sql", "select 1"));
      findTool(tools, "db_list_tables").handler().apply(null, Map.of());

      assertEquals(2, recorded.size());
      assertEquals("not-configured", recorded.get(0).datasourceId());
      assertEquals("db_query", recorded.get(0).toolName());
      assertFalse(recorded.get(0).success());
      assertTrue(recorded.get(0).errorMessage().contains("Unknown datasourceId"));
      assertEquals(ToolInvocation.UNKNOWN_DATASOURCE, recorded.get(1).datasourceId());
      assertEquals("db_list_tables", recorded.get(1).toolName());
   }

   @Test
   void recordsASuccessfulCallAsASuccess() throws Exception {
      ConnectionManager connectionManager = mock(ConnectionManager.class);
      when(connectionManager.getConnection(any())).thenReturn(mock(Connection.class));
      JdbcExecutor executor = mock(JdbcExecutor.class);
      when(executor.toJson(any())).thenReturn("[]");
      List<ToolInvocation> recorded = new ArrayList<>();
      GenericMcpTools tools = new GenericMcpTools(
         new DatasourceRegistry(Map.of("my", datasourceContext(DatabaseType.MYSQL, new MySqlDialect(), connectionManager))),
         executor,
         recorded::add
      );

      findTool(tools, "db_query").handler().apply(null, Map.of("datasourceId", "my", "sql", "select 1"));

      assertEquals(1, recorded.size());
      assertTrue(recorded.get(0).success());
      assertNull(recorded.get(0).errorMessage());
      assertTrue(recorded.get(0).durationMillis() >= 0);
      assertNotNull(recorded.get(0).occurredAt());
   }

   @Test
   void countsAnAliasCallUnderItsOwnName() {
      List<ToolInvocation> recorded = new ArrayList<>();
      GenericMcpTools tools = new GenericMcpTools(new DatasourceRegistry(Map.of()), mock(JdbcExecutor.class), recorded::add);

      findTool(tools, "pg_list_tables").handler().apply(null, Map.of("datasourceId", "not-configured"));

      assertEquals(1, recorded.size());
      assertEquals("pg_list_tables", recorded.get(0).toolName());
   }

   @Test
   void leavesHandlersUnwrappedWithoutAListener() {
      GenericMcpTools tools = new GenericMcpTools(new DatasourceRegistry(Map.of()), mock(JdbcExecutor.class));

      McpSchema.CallToolResult result = findTool(tools, "db_query").handler().apply(null, Map.of("datasourceId", "not-configured", "sql", "select 1"));

      assertTrue(Boolean.TRUE.equals(result.isError()));
   }

   private static DatasourceContext datasourceContext(DatabaseType type, com.hbnrtech.mcp.dialect.DatabaseDialect dialect, ConnectionManager connectionManager) {
      return new DatasourceContext(
         new DatasourceConfig("ds", type, "jdbc:" + type.name().toLowerCase() + "://127.0.0.1:1/scratch", "app", "secret", "APP", null),
         dialect,
         connectionManager
      );
   }

   private static String callCompareSchemas(GenericMcpTools tools, Map<String, Object> args) {
      McpSchema.CallToolResult result = findTool(tools, "db_compare_schemas").handler().apply(null, args);
      StringBuilder text = new StringBuilder();
      for (McpSchema.Content content : result.content()) {
         if (content instanceof McpSchema.TextContent textContent) {
            text.append(textContent.text());
         }
      }
      return text.toString();
   }

   private static RegisteredTool findTool(GenericMcpTools tools, String name) {
      List<RegisteredTool> registered = tools.getRegisteredTools();
      return registered.stream().filter(tool -> name.equals(tool.tool().name())).findFirst().orElseThrow();
   }

   private static boolean hasSchemaProperty(GenericMcpTools tools, String name) {
      return schemaProperties(findTool(tools, name)).containsKey("schema");
   }

   private static Map<?, ?> schemaProperties(RegisteredTool tool) {
      Object properties = tool.tool().inputSchema().get("properties");
      return properties instanceof Map<?, ?> map ? map : Map.of();
   }
}
