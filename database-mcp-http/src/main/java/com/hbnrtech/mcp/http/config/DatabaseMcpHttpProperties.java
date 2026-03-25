package com.hbnrtech.mcp.http.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "database-mcp.http")
public class DatabaseMcpHttpProperties {
   private String endpoint = "/mcp";
   private String configDbPath = "data/database-mcp-config.db";
   private Duration keepAliveInterval = Duration.ofSeconds(60);
   private boolean disallowDelete;
   private boolean apiKeyEnabled;
   private String apiKeyHeader = "X-API-Key";
   private String apiKeySecret = "change-me-secret";
   private long apiKeyTtlSeconds = 300;
   private long apiKeyAllowedClockSkewSeconds = 30;
   private String adminApiBasePath = "/admin/api";
   private String adminPasswordHeader = "X-Admin-Password";
   private String adminPassword = "";
   private Map<String, BaseJdbcConfigProperties> baseJdbcConfigs = new LinkedHashMap<>();
   private Map<String, DatasourceProperties> datasources = new LinkedHashMap<>();

   public String getEndpoint() {
      return this.endpoint;
   }

   public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
   }

   public String getConfigDbPath() {
      return this.configDbPath;
   }

   public void setConfigDbPath(String configDbPath) {
      this.configDbPath = configDbPath;
   }

   public Duration getKeepAliveInterval() {
      return this.keepAliveInterval;
   }

   public void setKeepAliveInterval(Duration keepAliveInterval) {
      this.keepAliveInterval = keepAliveInterval;
   }

   public boolean isDisallowDelete() {
      return this.disallowDelete;
   }

   public void setDisallowDelete(boolean disallowDelete) {
      this.disallowDelete = disallowDelete;
   }

   public boolean isApiKeyEnabled() {
      return this.apiKeyEnabled;
   }

   public void setApiKeyEnabled(boolean apiKeyEnabled) {
      this.apiKeyEnabled = apiKeyEnabled;
   }

   public String getApiKeyHeader() {
      return this.apiKeyHeader;
   }

   public void setApiKeyHeader(String apiKeyHeader) {
      this.apiKeyHeader = apiKeyHeader;
   }

   public String getApiKeySecret() {
      return this.apiKeySecret;
   }

   public void setApiKeySecret(String apiKeySecret) {
      this.apiKeySecret = apiKeySecret;
   }

   public long getApiKeyTtlSeconds() {
      return this.apiKeyTtlSeconds;
   }

   public void setApiKeyTtlSeconds(long apiKeyTtlSeconds) {
      this.apiKeyTtlSeconds = apiKeyTtlSeconds;
   }

   public long getApiKeyAllowedClockSkewSeconds() {
      return this.apiKeyAllowedClockSkewSeconds;
   }

   public void setApiKeyAllowedClockSkewSeconds(long apiKeyAllowedClockSkewSeconds) {
      this.apiKeyAllowedClockSkewSeconds = apiKeyAllowedClockSkewSeconds;
   }

   public String getAdminApiBasePath() {
      return this.adminApiBasePath;
   }

   public void setAdminApiBasePath(String adminApiBasePath) {
      this.adminApiBasePath = adminApiBasePath;
   }

   public String getAdminPasswordHeader() {
      return this.adminPasswordHeader;
   }

   public void setAdminPasswordHeader(String adminPasswordHeader) {
      this.adminPasswordHeader = adminPasswordHeader;
   }

   public String getAdminPassword() {
      return this.adminPassword;
   }

   public void setAdminPassword(String adminPassword) {
      this.adminPassword = adminPassword;
   }

   public Map<String, BaseJdbcConfigProperties> getBaseJdbcConfigs() {
      return this.baseJdbcConfigs;
   }

   public void setBaseJdbcConfigs(Map<String, BaseJdbcConfigProperties> baseJdbcConfigs) {
      this.baseJdbcConfigs = baseJdbcConfigs;
   }

   public Map<String, DatasourceProperties> getDatasources() {
      return this.datasources;
   }

   public void setDatasources(Map<String, DatasourceProperties> datasources) {
      this.datasources = datasources;
   }

   public static class BaseJdbcConfigProperties {
      private String type;
      private String host;
      private int port;
      private String databaseName;
      private String sid;
      private String jdbcParams;

      public String getType() {
         return this.type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public String getHost() {
         return this.host;
      }

      public void setHost(String host) {
         this.host = host;
      }

      public int getPort() {
         return this.port;
      }

      public void setPort(int port) {
         this.port = port;
      }

      public String getDatabaseName() {
         return this.databaseName;
      }

      public void setDatabaseName(String databaseName) {
         this.databaseName = databaseName;
      }

      public String getSid() {
         return this.sid;
      }

      public void setSid(String sid) {
         this.sid = sid;
      }

      public String getJdbcParams() {
         return this.jdbcParams;
      }

      public void setJdbcParams(String jdbcParams) {
         this.jdbcParams = jdbcParams;
      }
   }

   public static class DatasourceProperties {
      private String baseConfigId;
      private String username;
      private String password = "";
      private String schema;

      public String getBaseConfigId() {
         return this.baseConfigId;
      }

      public void setBaseConfigId(String baseConfigId) {
         this.baseConfigId = baseConfigId;
      }

      public String getUsername() {
         return this.username;
      }

      public void setUsername(String username) {
         this.username = username;
      }

      public String getPassword() {
         return this.password;
      }

      public void setPassword(String password) {
         this.password = password;
      }

      public String getSchema() {
         return this.schema;
      }

      public void setSchema(String schema) {
         this.schema = schema;
      }
   }
}
