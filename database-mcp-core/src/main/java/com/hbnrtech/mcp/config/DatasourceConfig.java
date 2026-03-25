package com.hbnrtech.mcp.config;

public record DatasourceConfig(
   String id,
   DatabaseType type,
   String url,
   String username,
   String password,
   String defaultSchema
) {
   public static DatasourceConfig fromDatabaseConfig(String id, DatabaseConfig config) {
      return new DatasourceConfig(id, config.type(), config.url(), config.username(), config.password(), config.defaultSchema());
   }
}
