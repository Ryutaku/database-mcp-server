package com.hbnrtech.mcp.config;

import java.time.Duration;
import java.util.Map;

public record HttpServerConfig(
   String host,
   int port,
   String endpoint,
   Duration keepAliveInterval,
   boolean disallowDelete
) {
   private static final String DEFAULT_HOST = "0.0.0.0";
   private static final int DEFAULT_PORT = 8080;
   private static final String DEFAULT_ENDPOINT = "/mcp";
   private static final Duration DEFAULT_KEEP_ALIVE = Duration.ofSeconds(15);

   public static HttpServerConfig fromEnv() {
      return from(System.getenv());
   }

   public static HttpServerConfig from(Map<String, String> env) {
      String host = firstNonBlank(env.get("MCP_HTTP_HOST"), DEFAULT_HOST);
      int port = parsePort(env.get("MCP_HTTP_PORT"), DEFAULT_PORT);
      String endpoint = normalizeEndpoint(env.get("MCP_HTTP_ENDPOINT"));
      Duration keepAliveInterval = parseKeepAliveInterval(env.get("MCP_HTTP_KEEPALIVE_SECONDS"));
      boolean disallowDelete = Boolean.parseBoolean(firstNonBlank(env.get("MCP_HTTP_DISALLOW_DELETE"), "false"));
      return new HttpServerConfig(host, port, endpoint, keepAliveInterval, disallowDelete);
   }

   public String baseUrl() {
      return "http://" + host + ":" + port;
   }

   private static int parsePort(String rawPort, int fallback) {
      if (rawPort == null || rawPort.isBlank()) {
         return fallback;
      }
      try {
         int port = Integer.parseInt(rawPort.trim());
         if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("MCP_HTTP_PORT must be between 1 and 65535");
         }
         return port;
      } catch (NumberFormatException ex) {
         throw new IllegalArgumentException("Invalid MCP_HTTP_PORT: " + rawPort, ex);
      }
   }

   private static Duration parseKeepAliveInterval(String rawSeconds) {
      if (rawSeconds == null || rawSeconds.isBlank()) {
         return DEFAULT_KEEP_ALIVE;
      }
      try {
         long seconds = Long.parseLong(rawSeconds.trim());
         if (seconds < 1) {
            throw new IllegalArgumentException("MCP_HTTP_KEEPALIVE_SECONDS must be greater than 0");
         }
         return Duration.ofSeconds(seconds);
      } catch (NumberFormatException ex) {
         throw new IllegalArgumentException("Invalid MCP_HTTP_KEEPALIVE_SECONDS: " + rawSeconds, ex);
      }
   }

   private static String normalizeEndpoint(String endpoint) {
      String normalized = firstNonBlank(endpoint, DEFAULT_ENDPOINT).trim();
      if (!normalized.startsWith("/")) {
         normalized = "/" + normalized;
      }
      if (normalized.length() > 1 && normalized.endsWith("/")) {
         normalized = normalized.substring(0, normalized.length() - 1);
      }
      return normalized;
   }

   private static String firstNonBlank(String value, String fallback) {
      return value != null && !value.isBlank() ? value : fallback;
   }
}
