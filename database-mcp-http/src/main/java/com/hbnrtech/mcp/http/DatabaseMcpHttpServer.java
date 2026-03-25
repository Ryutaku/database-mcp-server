package com.hbnrtech.mcp.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.hbnrtech.mcp.http")
public class DatabaseMcpHttpServer {
   public static void main(String[] args) {
      SpringApplication.run(DatabaseMcpHttpServer.class, args);
   }
}
