package com.hbnrtech.mcp.bootstrap;

import com.hbnrtech.mcp.config.DatabaseConfig;
import com.hbnrtech.mcp.config.DatasourceConfig;
import com.hbnrtech.mcp.dialect.DatabaseDialect;
import com.hbnrtech.mcp.dialect.DatabaseDialectFactory;
import com.hbnrtech.mcp.execution.ConnectionManager;
import com.hbnrtech.mcp.execution.DatasourceContext;
import com.hbnrtech.mcp.execution.DatasourceRegistry;
import com.hbnrtech.mcp.execution.JdbcExecutor;
import com.hbnrtech.mcp.tools.GenericMcpTools;
import com.hbnrtech.mcp.tools.ToolRegistry;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DatabaseMcpRuntimeFactory {
   private DatabaseMcpRuntimeFactory() {
   }

   public static DatabaseMcpRuntime createRuntime() {
      DatabaseConfig config = DatabaseConfig.fromEnv();
      DatasourceConfig datasourceConfig = DatasourceConfig.fromDatabaseConfig("default", config);
      return createRuntime(Map.of(datasourceConfig.id(), datasourceConfig));
   }

   public static DatabaseMcpRuntime createRuntime(Map<String, DatasourceConfig> datasourceConfigs) {
      Map<String, DatasourceContext> datasourceContexts = new LinkedHashMap<>();
      for (DatasourceConfig datasourceConfig : datasourceConfigs.values()) {
         datasourceContexts.put(datasourceConfig.id(), createDatasourceContext(datasourceConfig));
      }

      DatasourceRegistry datasourceRegistry = new DatasourceRegistry(datasourceContexts);
      JdbcExecutor executor = new JdbcExecutor();
      GenericMcpTools tools = new GenericMcpTools(datasourceRegistry, executor);
      return new DatabaseMcpRuntime(datasourceRegistry, executor, tools);
   }

   public static DatasourceContext createDatasourceContext(DatasourceConfig datasourceConfig) {
      DatabaseDialect dialect = DatabaseDialectFactory.create(datasourceConfig.type());
      ConnectionManager connectionManager = new ConnectionManager(
         new DatabaseConfig(
            datasourceConfig.type(),
            datasourceConfig.url(),
            datasourceConfig.username(),
            datasourceConfig.password(),
            datasourceConfig.defaultSchema()
         ),
         dialect
      );
      return new DatasourceContext(datasourceConfig, dialect, connectionManager);
   }

   public static void configureServer(McpServer.SyncSpecification<?> builder, McpJsonMapper jsonMapper, DatabaseMcpRuntime runtime) {
      builder.jsonMapper(jsonMapper).serverInfo("database-mcp-server", "1.1.0");
      ToolRegistry.register(builder, runtime.tools());
   }

   public static void shutdown(String serverLabel, DatabaseMcpRuntime runtime) {
      System.err.println("Shutting down " + serverLabel + "...");
      runtime.datasourceRegistry().close();
      System.err.println("Datasource pools closed.");
   }
}
