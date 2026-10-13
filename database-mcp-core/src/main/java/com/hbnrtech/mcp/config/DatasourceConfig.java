package com.hbnrtech.mcp.config;

import java.util.List;

public record DatasourceConfig(
   String id,
   DatabaseType type,
   String url,
   String username,
   String password,
   String defaultSchema,
   List<String> executeBlacklistRuleIds
) {
   /**
    * @param executeBlacklistRuleIds the {@code db_execute} blacklist to apply. {@code null} means
    *                                "use the dialect defaults", an empty list means "block nothing".
    *                                The distinction matters: a datasource saved from the admin
    *                                console always carries an explicit list, while a datasource
    *                                built from environment variables carries {@code null} and keeps
    *                                the behaviour it has always had.
    */
   public static DatasourceConfig fromDatabaseConfig(String id, DatabaseConfig config) {
      return new DatasourceConfig(id, config.type(), config.url(), config.username(), config.password(), config.defaultSchema(), null);
   }
}
