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
   private String adminPasswordHeader = "X-Admin-Password";
   private String adminPassword = "";

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
}