package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.bootstrap.DatabaseMcpRuntimeFactory;
import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.config.DatasourceConfig;
import com.hbnrtech.mcp.execution.DatasourceContext;
import com.hbnrtech.mcp.execution.DatasourceRegistry;
import com.hbnrtech.mcp.http.admin.ConfigModels.BaseJdbcConfigPayload;
import com.hbnrtech.mcp.http.admin.ConfigModels.DatasourcePayload;
import com.hbnrtech.mcp.http.admin.ConfigModels.RuntimeSnapshot;
import com.hbnrtech.mcp.http.admin.ConfigModels.StoredBaseJdbcConfig;
import com.hbnrtech.mcp.http.admin.ConfigModels.StoredDatasource;
import com.hbnrtech.mcp.http.admin.ConfigModels.TestConnectionResult;
import java.sql.Connection;
import java.sql.SQLException;
import com.hbnrtech.mcp.http.config.DatabaseMcpHttpProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RuntimeConfigurationService {
   private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeConfigurationService.class);

   private final SqliteConfigRepository repository;
   private final DatasourceRegistry datasourceRegistry;

   public RuntimeConfigurationService(SqliteConfigRepository repository, DatabaseMcpHttpProperties properties) {
      this.repository = repository;
      RuntimeSnapshot bootstrap = this.bootstrapSnapshot(properties);
      if (this.repository.isEmpty() && (!bootstrap.baseConfigs().isEmpty() || !bootstrap.datasources().isEmpty())) {
         this.repository.seed(bootstrap);
         LOGGER.info("Seeded SQLite config store from application.yml");
      }

      RuntimeSnapshot snapshot = this.repository.loadSnapshot();
      this.datasourceRegistry = new DatasourceRegistry(this.buildDatasourceContexts(snapshot.datasourceConfigs()));
      LOGGER.info("Loaded {} base JDBC configs and {} datasources from SQLite config store", snapshot.baseConfigs().size(), snapshot.datasources().size());
   }

   public DatasourceRegistry datasourceRegistry() {
      return this.datasourceRegistry;
   }

   public RuntimeSnapshot currentSnapshot() {
      return this.repository.loadSnapshot();
   }

   public synchronized RuntimeSnapshot upsertBaseConfig(String baseConfigId, BaseJdbcConfigPayload payload) {
      validateId(baseConfigId, "baseConfigId");
      DatabaseType type = DatabaseType.from(payload.type());
      requireNonBlank(payload.host(), "host");
      requirePort(payload.port());
      if (type == DatabaseType.POSTGRES) {
         requireNonBlank(payload.databaseName(), "databaseName");
      }
      if (type == DatabaseType.ORACLE) {
         requireNonBlank(payload.sid(), "sid");
      }

      this.repository.upsertBaseConfig(
         new StoredBaseJdbcConfig(
            baseConfigId,
            type,
            payload.host(),
            payload.port(),
            blankToNull(payload.databaseName()),
            blankToNull(payload.sid()),
            blankToNull(payload.jdbcParams())
         )
      );
      return this.reload();
   }

   public synchronized RuntimeSnapshot deleteBaseConfig(String baseConfigId) {
      validateId(baseConfigId, "baseConfigId");
      this.repository.deleteBaseConfig(baseConfigId);
      return this.reload();
   }

   public synchronized RuntimeSnapshot upsertDatasource(String datasourceId, DatasourcePayload payload) {
      validateId(datasourceId, "datasourceId");
      validateId(payload.baseConfigId(), "baseConfigId");
      requireNonBlank(payload.username(), "username");
      if (this.currentSnapshot().baseConfigs().stream().noneMatch(item -> item.id().equals(payload.baseConfigId()))) {
         throw new IllegalArgumentException("Unknown baseConfigId: " + payload.baseConfigId());
      }
      this.repository.upsertDatasource(
         new StoredDatasource(
            datasourceId,
            payload.baseConfigId(),
            payload.username(),
            payload.password() == null ? "" : payload.password(),
            blankToNull(payload.schema())
         )
      );
      return this.reload();
   }

   public synchronized RuntimeSnapshot deleteDatasource(String datasourceId) {
      validateId(datasourceId, "datasourceId");
      this.repository.deleteDatasource(datasourceId);
      return this.reload();
   }

   public TestConnectionResult testDatasource(String datasourceId) {
      validateId(datasourceId, "datasourceId");
      RuntimeSnapshot snapshot = this.currentSnapshot();
      DatasourceConfig datasourceConfig = snapshot.datasourceConfigs().get(datasourceId);
      if (datasourceConfig == null) {
         throw new IllegalArgumentException("Unknown datasourceId: " + datasourceId);
      }
      DatasourceContext context = DatabaseMcpRuntimeFactory.createDatasourceContext(datasourceConfig);
      return this.testContext(context, "Datasource '" + datasourceId + "'");
   }

   private RuntimeSnapshot reload() {
      RuntimeSnapshot snapshot = this.repository.loadSnapshot();
      this.datasourceRegistry.replaceAll(this.buildDatasourceContexts(snapshot.datasourceConfigs()));
      LOGGER.info("Reloaded runtime config: {} base JDBC configs, {} datasources", snapshot.baseConfigs().size(), snapshot.datasources().size());
      return snapshot;
   }

   private TestConnectionResult testContext(DatasourceContext context, String label) {
      try (Connection ignored = context.connectionManager().getConnection(context.config().defaultSchema())) {
         return new TestConnectionResult(true, label + " connected successfully");
      } catch (SQLException ex) {
         return new TestConnectionResult(false, label + " failed: " + ex.getMessage());
      } finally {
         context.connectionManager().close();
      }
   }

   private Map<String, DatasourceContext> buildDatasourceContexts(Map<String, DatasourceConfig> datasourceConfigs) {
      Map<String, DatasourceContext> contexts = new LinkedHashMap<>();
      for (DatasourceConfig datasourceConfig : datasourceConfigs.values()) {
         contexts.put(datasourceConfig.id(), DatabaseMcpRuntimeFactory.createDatasourceContext(datasourceConfig));
      }
      return contexts;
   }

   private RuntimeSnapshot bootstrapSnapshot(DatabaseMcpHttpProperties properties) {
      List<StoredBaseJdbcConfig> baseConfigs = new ArrayList<>();
      for (Map.Entry<String, DatabaseMcpHttpProperties.BaseJdbcConfigProperties> entry : properties.getBaseJdbcConfigs().entrySet()) {
         DatabaseMcpHttpProperties.BaseJdbcConfigProperties config = entry.getValue();
         baseConfigs.add(
            new StoredBaseJdbcConfig(
               entry.getKey(),
               DatabaseType.from(config.getType()),
               config.getHost(),
               config.getPort(),
               blankToNull(config.getDatabaseName()),
               blankToNull(config.getSid()),
               blankToNull(config.getJdbcParams())
            )
         );
      }

      List<StoredDatasource> datasources = new ArrayList<>();
      for (Map.Entry<String, DatabaseMcpHttpProperties.DatasourceProperties> entry : properties.getDatasources().entrySet()) {
         DatabaseMcpHttpProperties.DatasourceProperties datasource = entry.getValue();
         datasources.add(
            new StoredDatasource(
               entry.getKey(),
               datasource.getBaseConfigId(),
               datasource.getUsername(),
               datasource.getPassword() == null ? "" : datasource.getPassword(),
               blankToNull(datasource.getSchema())
            )
         );
      }

      return new RuntimeSnapshot(baseConfigs, datasources);
   }

   private static void validateId(String value, String fieldName) {
      requireNonBlank(value, fieldName);
      if (!value.matches("[A-Za-z0-9_.-]+")) {
         throw new IllegalArgumentException(fieldName + " contains unsupported characters");
      }
   }

   private static void requireNonBlank(String value, String fieldName) {
      if (value == null || value.isBlank()) {
         throw new IllegalArgumentException(fieldName + " is required");
      }
   }

   private static void requirePort(Integer value) {
      if (value == null || value < 1 || value > 65535) {
         throw new IllegalArgumentException("port must be between 1 and 65535");
      }
   }

   private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value;
   }
}
