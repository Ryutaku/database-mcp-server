package com.hbnrtech.mcp.http.config;

import com.hbnrtech.mcp.bootstrap.DatabaseMcpRuntime;
import com.hbnrtech.mcp.bootstrap.DatabaseMcpRuntimeFactory;
import com.hbnrtech.mcp.execution.JdbcExecutor;
import com.hbnrtech.mcp.http.admin.AdminPasswordAuthenticationFilter;
import com.hbnrtech.mcp.http.admin.RuntimeConfigurationService;
import com.hbnrtech.mcp.tools.GenericMcpTools;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpHttpServerConfiguration {
   private static final Logger LOGGER = LoggerFactory.getLogger(McpHttpServerConfiguration.class);

   @Bean
   public DatabaseMcpRuntime databaseMcpRuntime(RuntimeConfigurationService runtimeConfigurationService) {
      JdbcExecutor executor = new JdbcExecutor();
      GenericMcpTools tools = new GenericMcpTools(runtimeConfigurationService.datasourceRegistry(), executor);
      DatabaseMcpRuntime runtime = new DatabaseMcpRuntime(runtimeConfigurationService.datasourceRegistry(), executor, tools);
      LOGGER.info("Loaded {} datasource definitions", runtime.datasourceRegistry().datasources().size());
      runtime.datasourceRegistry().datasources().forEach((id, context) ->
         LOGGER.info("Datasource [{}] -> {} {}", id, context.config().type(), context.config().url())
      );
      return runtime;
   }

   @Bean
   public HttpServletStreamableServerTransportProvider mcpTransportProvider(DatabaseMcpHttpProperties properties) {
      return HttpServletStreamableServerTransportProvider.builder()
         .jsonMapper(McpJsonMapper.createDefault())
         .mcpEndpoint(properties.getEndpoint())
         .keepAliveInterval(properties.getKeepAliveInterval())
         .disallowDelete(properties.isDisallowDelete())
         .build();
   }

   @Bean
   public McpSyncServer mcpSyncServer(HttpServletStreamableServerTransportProvider transport, DatabaseMcpRuntime runtime) {
      var builder = McpServer.sync(transport);
      DatabaseMcpRuntimeFactory.configureServer(builder, McpJsonMapper.createDefault(), runtime);
      McpSyncServer server = builder.build();
      LOGGER.info("Registered {} MCP tools", runtime.tools().getRegisteredTools().size());
      return server;
   }

   @Bean
   public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServletRegistration(
      HttpServletStreamableServerTransportProvider transport,
      DatabaseMcpHttpProperties properties
   ) {
      LOGGER.info("Registering MCP servlet at {}", properties.getEndpoint());
      return new ServletRegistrationBean<>(transport, properties.getEndpoint());
   }

   @Bean
   public FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyAuthenticationFilter(
      DatabaseMcpHttpProperties properties,
      ApiKeySignatureService apiKeySignatureService
   ) {
      FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>();
      registration.setFilter(new ApiKeyAuthenticationFilter(properties.isApiKeyEnabled(), properties.getApiKeyHeader(), apiKeySignatureService));
      registration.setUrlPatterns(java.util.List.of(properties.getEndpoint()));
      registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
      registration.setOrder(1);
      return registration;
   }

   @Bean
   public FilterRegistrationBean<McpRequestLoggingFilter> mcpRequestLoggingFilter(DatabaseMcpHttpProperties properties) {
      FilterRegistrationBean<McpRequestLoggingFilter> registration = new FilterRegistrationBean<>();
      registration.setFilter(new McpRequestLoggingFilter());
      registration.setUrlPatterns(java.util.List.of(properties.getEndpoint()));
      registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
      registration.setOrder(2);
      return registration;
   }

   @Bean
   public FilterRegistrationBean<AdminPasswordAuthenticationFilter> adminPasswordAuthenticationFilter(DatabaseMcpHttpProperties properties) {
      FilterRegistrationBean<AdminPasswordAuthenticationFilter> registration = new FilterRegistrationBean<>();
      registration.setFilter(new AdminPasswordAuthenticationFilter(properties.getAdminPasswordHeader(), properties.getAdminPassword()));
      registration.setUrlPatterns(java.util.List.of(properties.getAdminApiBasePath() + "/*"));
      registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
      registration.setOrder(0);
      return registration;
   }
}
