package com.hbnrtech.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpServerConfigTest {
   @Test
   void usesDefaultsWhenHttpVariablesAreMissing() {
      HttpServerConfig config = HttpServerConfig.from(Map.of());

      assertEquals("0.0.0.0", config.host());
      assertEquals(8080, config.port());
      assertEquals("/mcp", config.endpoint());
      assertEquals(Duration.ofSeconds(15), config.keepAliveInterval());
      assertFalse(config.disallowDelete());
   }

   @Test
   void normalizesConfiguredValues() {
      HttpServerConfig config = HttpServerConfig.from(
         Map.of(
            "MCP_HTTP_HOST", "127.0.0.1",
            "MCP_HTTP_PORT", "9090",
            "MCP_HTTP_ENDPOINT", "api/mcp/",
            "MCP_HTTP_KEEPALIVE_SECONDS", "30",
            "MCP_HTTP_DISALLOW_DELETE", "true"
         )
      );

      assertEquals("127.0.0.1", config.host());
      assertEquals(9090, config.port());
      assertEquals("/api/mcp", config.endpoint());
      assertEquals(Duration.ofSeconds(30), config.keepAliveInterval());
      assertTrue(config.disallowDelete());
   }

   @Test
   void rejectsInvalidPort() {
      assertThrows(IllegalArgumentException.class, () -> HttpServerConfig.from(Map.of("MCP_HTTP_PORT", "70000")));
   }
}
