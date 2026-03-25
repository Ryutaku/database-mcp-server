package com.hbnrtech.mcp.http.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class McpRequestLoggingFilter extends OncePerRequestFilter {
   private static final Logger LOGGER = LoggerFactory.getLogger(McpRequestLoggingFilter.class);
   private static final int MAX_BODY_LENGTH = 4000;

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
      ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
      ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
      long start = System.currentTimeMillis();
      try {
         filterChain.doFilter(requestWrapper, responseWrapper);
         long cost = System.currentTimeMillis() - start;
         LOGGER.info(
            "MCP HTTP {} {} from {} -> {} in {} ms | requestHeaders={} | requestBody={} | responseHeaders={} | responseBody={}",
            requestWrapper.getMethod(),
            requestWrapper.getRequestURI(),
            requestWrapper.getRemoteAddr(),
            responseWrapper.getStatus(),
            cost,
            summarizeHeaders(requestWrapper),
            sanitizeBody(bodyAsString(requestWrapper.getContentAsByteArray(), requestWrapper.getCharacterEncoding())),
            summarizeHeaders(responseWrapper),
            sanitizeBody(bodyAsString(responseWrapper.getContentAsByteArray(), responseWrapper.getCharacterEncoding()))
         );
      } finally {
         responseWrapper.copyBodyToResponse();
      }
   }

   private static String summarizeHeaders(HttpServletRequest request) {
      return "{content-type=" + nullToEmpty(request.getContentType())
         + ", accept=" + nullToEmpty(request.getHeader("Accept"))
         + ", mcp-session-id=" + nullToEmpty(request.getHeader("mcp-session-id"))
         + "}";
   }

   private static String summarizeHeaders(HttpServletResponse response) {
      return "{content-type=" + nullToEmpty(response.getContentType()) + "}";
   }

   private static String bodyAsString(byte[] body, String encoding) {
      if (body == null || body.length == 0) {
         return "<empty>";
      }
      Charset charset = Optional.ofNullable(encoding)
         .map(name -> {
            try {
               return Charset.forName(name);
            } catch (Exception ex) {
               return StandardCharsets.UTF_8;
            }
         })
         .orElse(StandardCharsets.UTF_8);
      return new String(body, charset);
   }

   private static String sanitizeBody(String body) {
      if (body == null || body.isBlank()) {
         return "<empty>";
      }

      String normalized = body.replace('\r', ' ').replace('\n', ' ').trim();
      normalized = normalized.replaceAll("\\s{2,}", " ");
      normalized = normalized.replaceAll("\"stackTrace\"\\s*:\\s*\\[.*?\\]", "\"stackTrace\":\"<omitted>\"");
      normalized = normalized.replaceAll("\"cause\"\\s*:\\s*\\{.*?\\}", "\"cause\":\"<omitted>\"");

      if (normalized.length() > MAX_BODY_LENGTH) {
         return normalized.substring(0, MAX_BODY_LENGTH) + "...(truncated)";
      }
      return normalized;
   }

   private static String nullToEmpty(String value) {
      return value == null ? "" : value;
   }
}
