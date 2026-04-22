package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.config.DatasourceConfig;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
         return this.jdbcUrl(null);
      }

      public String jdbcUrl(String schema) {
         return switch (this.type) {
            case POSTGRES -> {
               String suffix = buildPostgresSuffix(this.jdbcParams, schema);
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
            baseConfig.jdbcUrl(this.schema),
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

   private static String buildPostgresSuffix(String jdbcParams, String schema) {
      Map<String, String> params = new LinkedHashMap<>();
      String normalizedParams = jdbcParams == null ? "" : jdbcParams.trim();
      if (!normalizedParams.isBlank()) {
         for (String entry : normalizedParams.split("&")) {
            if (entry == null || entry.isBlank()) {
               continue;
            }
            int separator = entry.indexOf('=');
            if (separator < 0) {
               params.put(entry, "");
            } else {
               params.put(entry.substring(0, separator), entry.substring(separator + 1));
            }
         }
      }

      if (schema != null && !schema.isBlank()) {
         params.put("currentSchema", URLEncoder.encode(schema, StandardCharsets.UTF_8));
      }

      if (params.isEmpty()) {
         return "";
      }

      StringBuilder suffix = new StringBuilder("?");
      boolean first = true;
      for (Map.Entry<String, String> entry : params.entrySet()) {
         if (!first) {
            suffix.append("&");
         }
         first = false;
         suffix.append(entry.getKey());
         if (!entry.getValue().isEmpty()) {
            suffix.append("=").append(entry.getValue());
         }
      }
      return suffix.toString();
   }
}
