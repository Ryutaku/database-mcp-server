package com.hbnrtech.mcp.http.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminPasswordAuthenticationFilter extends OncePerRequestFilter {
   private final String usernameHeaderName;
   private final String passwordHeaderName;
   private final AdminCredentialService credentialService;

   public AdminPasswordAuthenticationFilter(
      String usernameHeaderName,
      String passwordHeaderName,
      AdminCredentialService credentialService
   ) {
      this.usernameHeaderName = usernameHeaderName;
      this.passwordHeaderName = passwordHeaderName;
      this.credentialService = credentialService;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
      String username = request.getHeader(this.usernameHeaderName);
      String password = request.getHeader(this.passwordHeaderName);
      if (!this.credentialService.authenticate(username, password)) {
         response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid admin credentials");
         return;
      }

      filterChain.doFilter(request, response);
   }
}
