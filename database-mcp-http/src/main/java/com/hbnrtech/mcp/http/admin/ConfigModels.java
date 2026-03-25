package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.config.DatasourceConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigModels {
   private ConfigModels() {
   }

   public record StoredBaseJdbcConfig(
      String id,
      DatabaseType type,
      String host,
      int port,
      String databaseName,
      String sid,
      String jdbcParams
   ) {
      public String jdbcUrl() {
         return switch (this.type) {
            case POSTGRES -> {
               String suffix = this.jdbcParams == null || this.jdbcParams.isBlank() ? "" : "?" + this.jdbcParams;
               yield "jdbc:postgresql://" + this.host + ":" + this.port + "/" + this.databaseName + suffix;
            }
            case ORACLE -> "jdbc:oracle:thin:@" + this.host + ":" + this.port + ":" + this.sid;
         };
      }
   }

   public record StoredDatasource(
      String id,
      String baseConfigId,
      String username,
      String password,
      String schema
   ) {
      public DatasourceConfig toDatasourceConfig(StoredBaseJdbcConfig baseConfig) {
         return new DatasourceConfig(
            this.id,
            baseConfig.type(),
            baseConfig.jdbcUrl(),
            this.username(),
            this.password(),
            this.schema
         );
      }
   }

   public record RuntimeSnapshot(
      List<StoredBaseJdbcConfig> baseConfigs,
      List<StoredDatasource> datasources
   ) {
      public Map<String, DatasourceConfig> datasourceConfigs() {
         Map<String, StoredBaseJdbcConfig> baseConfigMap = new LinkedHashMap<>();
         for (StoredBaseJdbcConfig baseConfig : this.baseConfigs) {
            baseConfigMap.put(baseConfig.id(), baseConfig);
         }

         Map<String, DatasourceConfig> result = new LinkedHashMap<>();
         for (StoredDatasource datasource : this.datasources) {
            StoredBaseJdbcConfig baseConfig = baseConfigMap.get(datasource.baseConfigId());
            if (baseConfig == null) {
               throw new IllegalStateException("Missing base JDBC config: " + datasource.baseConfigId());
            }
            result.put(datasource.id(), datasource.toDatasourceConfig(baseConfig));
         }
         return result;
      }
   }

   public record BaseJdbcConfigPayload(
      String type,
      String host,
      Integer port,
      String databaseName,
      String sid,
      String jdbcParams
   ) {
   }

   public record DatasourcePayload(
      String baseConfigId,
      String username,
      String password,
      String schema
   ) {
   }

   public record TestConnectionResult(
      boolean success,
      String message
   ) {
   }
}
