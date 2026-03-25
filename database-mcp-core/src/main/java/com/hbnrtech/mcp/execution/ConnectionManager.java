package com.hbnrtech.mcp.execution;

import com.hbnrtech.mcp.config.DatabaseConfig;
import com.hbnrtech.mcp.dialect.DatabaseDialect;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class ConnectionManager {
   private final HikariDataSource dataSource;
   private final DatabaseDialect dialect;

   public ConnectionManager(DatabaseConfig config, DatabaseDialect dialect) {
      this.dialect = dialect;
      HikariConfig hikariConfig = new HikariConfig();
      hikariConfig.setJdbcUrl(config.url());
      hikariConfig.setUsername(config.username());
      hikariConfig.setPassword(config.password());
      hikariConfig.setMaximumPoolSize(5);
      hikariConfig.setMinimumIdle(0);
      hikariConfig.setIdleTimeout(300000L);
      hikariConfig.setConnectionTimeout(30000L);
      hikariConfig.setMaxLifetime(1800000L);
      hikariConfig.setKeepaliveTime(60000L);
      hikariConfig.setPoolName(dialect.serverName() + "Pool");
      hikariConfig.setValidationTimeout(5000L);
      // Do not fail application startup when a configured datasource is temporarily unavailable.
      hikariConfig.setInitializationFailTimeout(-1L);
      dialect.configureDataSource(hikariConfig);
      this.dataSource = new HikariDataSource(hikariConfig);
   }

   public Connection getConnection(String activeSchema) throws SQLException {
      Connection connection = this.dataSource.getConnection();
      try {
         if (activeSchema != null && !activeSchema.isBlank()) {
            this.dialect.applySessionContext(connection, activeSchema);
         }

         return connection;
      } catch (SQLException ex) {
         connection.close();
         throw ex;
      }
   }

   public void close() {
      if (!this.dataSource.isClosed()) {
         this.dataSource.close();
      }
   }
}
