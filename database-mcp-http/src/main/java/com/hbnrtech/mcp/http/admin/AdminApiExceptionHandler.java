package com.hbnrtech.mcp.http.admin;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.hbnrtech.mcp.http.admin")
public class AdminApiExceptionHandler {
   @ExceptionHandler(IllegalArgumentException.class)
   public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
      String message = ex.getMessage() == null ? "请求参数不合法" : ex.getMessage();
      return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(message);
   }
}
