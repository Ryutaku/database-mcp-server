package com.hbnrtech.mcp.http.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminPasswordAuthenticationFilter extends OncePerRequestFilter {
   private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

   private final String headerName;
   private final String configuredPassword;

   public AdminPasswordAuthenticationFilter(String headerName, String configuredPassword) {
      this.headerName = headerName;
      this.configuredPassword = configuredPassword;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
      String password = request.getHeader(this.headerName);
      if (password == null || !this.expectedPassword().equals(password)) {
         response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid admin password");
         return;
      }

      filterChain.doFilter(request, response);
   }

   private String expectedPassword() {
      if (this.configuredPassword != null && !this.configuredPassword.isBlank()) {
         return this.configuredPassword;
      }
      return LocalDate.now().format(DEFAULT_FORMATTER);
   }
}
