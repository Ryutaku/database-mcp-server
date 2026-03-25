package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.http.config.DatabaseMcpHttpProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class ConfigDataSourceConfiguration {
   @Bean
   public DataSource configStoreDataSource(DatabaseMcpHttpProperties properties) {
      Path dbPath = Paths.get(properties.getConfigDbPath()).toAbsolutePath().normalize();
      try {
         Path parent = dbPath.getParent();
         if (parent != null) {
            Files.createDirectories(parent);
         }
      } catch (Exception ex) {
         throw new IllegalStateException("Failed to prepare SQLite config path: " + dbPath, ex);
      }

      DriverManagerDataSource dataSource = new DriverManagerDataSource();
      dataSource.setDriverClassName("org.sqlite.JDBC");
      dataSource.setUrl("jdbc:sqlite:" + dbPath);
      return dataSource;
   }

   @Bean
   public JdbcTemplate configStoreJdbcTemplate(DataSource configStoreDataSource) {
      return new JdbcTemplate(configStoreDataSource);
   }
}
