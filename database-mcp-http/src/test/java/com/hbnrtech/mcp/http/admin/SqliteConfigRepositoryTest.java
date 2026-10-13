package com.hbnrtech.mcp.http.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.execution.ExecuteBlacklistRules;
import com.hbnrtech.mcp.http.admin.ConfigModels.StoredBaseJdbcConfig;
import com.hbnrtech.mcp.http.admin.ConfigModels.StoredDatasource;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SqliteConfigRepositoryTest {
   @TempDir
   Path tempDir;

   @Test
   void roundTripsTheConfiguredBlacklist() {
      SqliteConfigRepository repository = new SqliteConfigRepository(this.jdbcTemplate("round-trip"));
      repository.upsertBaseConfig(new StoredBaseJdbcConfig("ora", DatabaseType.ORACLE, "127.0.0.1", 1521, null, "FREEPDB1", null));

      repository.upsertDatasource(new StoredDatasource("ds", "ora", "app", "secret", null, List.of("drop_table", "truncate_table")));
      assertEquals(List.of("drop_table", "truncate_table"), this.blacklistOf(repository, "ds"));

      // An empty list must survive the round trip: it means "block nothing", not "use the defaults".
      repository.upsertDatasource(new StoredDatasource("ds", "ora", "app", "secret", null, List.of()));
      assertEquals(List.of(), this.blacklistOf(repository, "ds"));
   }

   @Test
   void givesDatasourcesCreatedBeforeTheColumnTheDefaultBlacklist() {
      JdbcTemplate jdbcTemplate = this.jdbcTemplate("legacy");
      this.createPreBlacklistSchema(jdbcTemplate);
      jdbcTemplate.update("insert into mcp_base_jdbc_config(id, type, host, port, sid) values ('ora', 'oracle', '127.0.0.1', 1521, 'FREEPDB1')");
      jdbcTemplate.update("insert into mcp_datasource(id, base_config_id, username, password) values ('ds', 'ora', 'app', 'secret')");

      SqliteConfigRepository repository = new SqliteConfigRepository(jdbcTemplate);

      assertTrue(this.columnNames(jdbcTemplate).contains("execute_blacklist"), "the migration should add the execute_blacklist column");
      assertEquals(ExecuteBlacklistRules.defaultRuleIds(DatabaseType.ORACLE), this.blacklistOf(repository, "ds"));
   }

   private List<String> columnNames(JdbcTemplate jdbcTemplate) {
      return jdbcTemplate.queryForList("pragma table_info(mcp_datasource)").stream()
         .map(row -> String.valueOf(row.get("name")))
         .toList();
   }

   private List<String> blacklistOf(SqliteConfigRepository repository, String datasourceId) {
      return repository.loadSnapshot().datasources().stream()
         .filter(item -> item.id().equals(datasourceId))
         .findFirst()
         .orElseThrow()
         .executeBlacklist();
   }

   /** The table shape from before the blacklist became configurable. */
   private void createPreBlacklistSchema(JdbcTemplate jdbcTemplate) {
      jdbcTemplate.execute(
         "create table mcp_base_jdbc_config (id text primary key, type text not null, host text not null, port integer not null, database_name text, sid text, jdbc_params text)"
      );
      jdbcTemplate.execute(
         "create table mcp_datasource (id text primary key, base_config_id text not null, username text not null, password text not null, schema_name text)"
      );
   }

   private JdbcTemplate jdbcTemplate(String name) {
      DriverManagerDataSource dataSource = new DriverManagerDataSource();
      dataSource.setDriverClassName("org.sqlite.JDBC");
      dataSource.setUrl("jdbc:sqlite:" + this.tempDir.resolve(name + ".db"));
      return new JdbcTemplate(dataSource);
   }
}
