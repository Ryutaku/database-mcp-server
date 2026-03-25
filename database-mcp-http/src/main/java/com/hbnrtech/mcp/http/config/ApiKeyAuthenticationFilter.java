package com.hbnrtech.mcp.http.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
   private final boolean enabled;
   private final String headerName;
   private final ApiKeySignatureService apiKeySignatureService;

   public ApiKeyAuthenticationFilter(boolean enabled, String headerName, ApiKeySignatureService apiKeySignatureService) {
      this.enabled = enabled;
      this.headerName = headerName;
      this.apiKeySignatureService = apiKeySignatureService;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
      if (!this.enabled) {
         filterChain.doFilter(request, response);
         return;
      }

      if (!this.apiKeySignatureService.isConfigured()) {
         response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "API key secret is not configured");
         return;
      }

      String apiKey = request.getHeader(this.headerName);
      if (!this.apiKeySignatureService.validate(apiKey)) {
         response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
         return;
      }

      filterChain.doFilter(request, response);
   }
}
