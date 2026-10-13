package com.hbnrtech.mcp.http.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
   private String adminUsernameHeader = "X-Admin-User";
   private String adminPasswordHeader = "X-Admin-Password";
   private String adminUsername = "admin";
   private String adminPassword = "";
   private final UsageStats usageStats = new UsageStats();

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

   public String getAdminUsernameHeader() {
      return this.adminUsernameHeader;
   }

   public void setAdminUsernameHeader(String adminUsernameHeader) {
      this.adminUsernameHeader = adminUsernameHeader;
   }

   public String getAdminPasswordHeader() {
      return this.adminPasswordHeader;
   }

   public void setAdminPasswordHeader(String adminPasswordHeader) {
      this.adminPasswordHeader = adminPasswordHeader;
   }

   public String getAdminUsername() {
      return this.adminUsername;
   }

   public void setAdminUsername(String adminUsername) {
      this.adminUsername = adminUsername;
   }

   public String getAdminPassword() {
      return this.adminPassword;
   }

   public void setAdminPassword(String adminPassword) {
      this.adminPassword = adminPassword;
   }

   public UsageStats getUsageStats() {
      return this.usageStats;
   }

   /**
    * How the hotspot ranking is fed. Every call is recorded, so the store needs a ceiling: the
    * retention window drops aged rows and {@code maxRows} caps the table no matter how busy the
    * server is.
    */
   public static class UsageStats {
      private boolean enabled = true;
      private int retentionDays = 30;
      private long maxRows = 200_000L;
      private Duration flushInterval = Duration.ofSeconds(1);
      private int queueCapacity = 10_000;

      public boolean isEnabled() {
         return this.enabled;
      }

      public void setEnabled(boolean enabled) {
         this.enabled = enabled;
      }

      public int getRetentionDays() {
         return this.retentionDays;
      }

      public void setRetentionDays(int retentionDays) {
         this.retentionDays = retentionDays;
      }

      public long getMaxRows() {
         return this.maxRows;
      }

      public void setMaxRows(long maxRows) {
         this.maxRows = maxRows;
      }

      public Duration getFlushInterval() {
         return this.flushInterval;
      }

      public void setFlushInterval(Duration flushInterval) {
         this.flushInterval = flushInterval;
      }

      public int getQueueCapacity() {
         return this.queueCapacity;
      }

      public void setQueueCapacity(int queueCapacity) {
         this.queueCapacity = queueCapacity;
      }
   }
}