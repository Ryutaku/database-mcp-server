package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.execution.ExecuteBlacklistRules;
import com.hbnrtech.mcp.http.admin.ConfigModels.RuntimeSnapshot;
import com.hbnrtech.mcp.http.admin.ConfigModels.StoredAdminCredential;
import com.hbnrtech.mcp.http.admin.ConfigModels.StoredBaseJdbcConfig;
import com.hbnrtech.mcp.http.admin.ConfigModels.StoredDatasource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SqliteConfigRepository {
   private static final Logger LOGGER = LoggerFactory.getLogger(SqliteConfigRepository.class);

   private final JdbcTemplate jdbcTemplate;

   public SqliteConfigRepository(JdbcTemplate configStoreJdbcTemplate) {
      this.jdbcTemplate = configStoreJdbcTemplate;
      this.initializeSchema();
   }

   public RuntimeSnapshot loadSnapshot() {
      List<StoredBaseJdbcConfig> baseConfigs = this.jdbcTemplate.query(
         """
         select id, type, host, port, database_name, sid, jdbc_params
         from mcp_base_jdbc_config
         order by id
         """,
         (rs, rowNum) -> new StoredBaseJdbcConfig(
            rs.getString("id"),
            DatabaseType.from(rs.getString("type")),
            rs.getString("host"),
            rs.getInt("port"),
            rs.getString("database_name"),
            rs.getString("sid"),
            rs.getString("jdbc_params")
         )
      );

      List<StoredDatasource> datasources = this.jdbcTemplate.query(
         """
         select id, base_config_id, username, password, schema_name, execute_blacklist
         from mcp_datasource
         order by id
         """,
         (rs, rowNum) -> new StoredDatasource(
            rs.getString("id"),
            rs.getString("base_config_id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("schema_name"),
            parseRuleIds(rs.getString("execute_blacklist"))
         )
      );

      return new RuntimeSnapshot(baseConfigs, datasources);
   }

   public Optional<StoredAdminCredential> loadAdminCredential() {
      List<StoredAdminCredential> rows = this.jdbcTemplate.query(
         """
         select username, salt, password_hash, updated_at
         from mcp_admin_credential
         where id = 1
         """,
         (rs, rowNum) -> new StoredAdminCredential(
            rs.getString("username"),
            rs.getString("salt"),
            rs.getString("password_hash"),
            rs.getString("updated_at")
         )
      );
      return rows.stream().findFirst();
   }

   public void upsertAdminCredential(StoredAdminCredential credential) {
      this.jdbcTemplate.update(
         """
         insert into mcp_admin_credential(id, username, salt, password_hash, updated_at)
         values (1, ?, ?, ?, ?)
         on conflict(id) do update set
           username = excluded.username,
           salt = excluded.salt,
           password_hash = excluded.password_hash,
           updated_at = excluded.updated_at
         """,
         credential.username(),
         credential.salt(),
         credential.passwordHash(),
         credential.updatedAt()
      );
   }

   public boolean isEmpty() {
      Integer count = this.jdbcTemplate.queryForObject("select count(*) from mcp_datasource", Integer.class);
      return count == null || count == 0;
   }

   public void upsertBaseConfig(StoredBaseJdbcConfig baseConfig) {
      this.jdbcTemplate.update(
         """
         insert into mcp_base_jdbc_config(id, type, host, port, database_name, sid, jdbc_params)
         values (?, ?, ?, ?, ?, ?, ?)
         on conflict(id) do update set
           type = excluded.type,
           host = excluded.host,
           port = excluded.port,
           database_name = excluded.database_name,
           sid = excluded.sid,
           jdbc_params = excluded.jdbc_params
         """,
         baseConfig.id(),
         baseConfig.type().name().toLowerCase(),
         baseConfig.host(),
         baseConfig.port(),
         baseConfig.databaseName(),
         baseConfig.sid(),
         baseConfig.jdbcParams()
      );
   }

   public void deleteBaseConfig(String baseConfigId) {
      this.jdbcTemplate.update("delete from mcp_datasource where base_config_id = ?", baseConfigId);
      this.jdbcTemplate.update("delete from mcp_base_jdbc_config where id = ?", baseConfigId);
   }

   public void upsertDatasource(StoredDatasource datasource) {
      this.jdbcTemplate.update(
         """
         insert into mcp_datasource(id, base_config_id, username, password, schema_name, execute_blacklist)
         values (?, ?, ?, ?, ?, ?)
         on conflict(id) do update set
           base_config_id = excluded.base_config_id,
           username = excluded.username,
           password = excluded.password,
           schema_name = excluded.schema_name,
           execute_blacklist = excluded.execute_blacklist
         """,
         datasource.id(),
         datasource.baseConfigId(),
         datasource.username(),
         datasource.password(),
         datasource.schema(),
         joinRuleIds(datasource.executeBlacklist())
      );
   }

   public void deleteDatasource(String datasourceId) {
      this.jdbcTemplate.update("delete from mcp_datasource where id = ?", datasourceId);
   }

   public void seed(RuntimeSnapshot snapshot) {
      for (StoredBaseJdbcConfig baseConfig : snapshot.baseConfigs()) {
         this.upsertBaseConfig(baseConfig);
      }
      for (StoredDatasource datasource : snapshot.datasources()) {
         this.upsertDatasource(datasource);
      }
   }

   private void initializeSchema() {
      this.jdbcTemplate.execute("pragma foreign_keys = on");
      this.migrateLegacySchemaIfRequired();
      this.jdbcTemplate.execute(
         """
         create table if not exists mcp_base_jdbc_config (
           id text primary key,
           type text not null,
           host text not null,
           port integer not null,
           database_name text,
           sid text,
           jdbc_params text
         )
         """
      );
      this.jdbcTemplate.execute(
         """
         create table if not exists mcp_datasource (
           id text primary key,
           base_config_id text not null,
           username text not null,
           password text not null,
           schema_name text,
           execute_blacklist text,
           foreign key (base_config_id) references mcp_base_jdbc_config(id) on delete cascade
         )
         """
      );
      this.jdbcTemplate.execute(
         """
         create table if not exists mcp_admin_credential (
           id integer primary key check (id = 1),
           username text not null,
           salt text not null,
           password_hash text not null,
           updated_at text not null
         )
         """
      );
      this.migrateExecuteBlacklist();
   }

   /**
    * Adds the blacklist column to databases created before it existed, then gives every datasource
    * that has no value the default rules for its database type. Datasources keep refusing exactly
    * what they refused before; the column only makes the existing behaviour visible and editable.
    */
   private void migrateExecuteBlacklist() {
      if (!this.tableExists("mcp_datasource")) {
         return;
      }

      if (!this.tableColumns("mcp_datasource").contains("execute_blacklist")) {
         this.jdbcTemplate.execute("alter table mcp_datasource add column execute_blacklist text");
         LOGGER.info("Added mcp_datasource.execute_blacklist to the config store");
      }

      List<BlacklistBackfillRow> rows = this.jdbcTemplate.query(
         """
         select d.id, b.type
         from mcp_datasource d
         join mcp_base_jdbc_config b on b.id = d.base_config_id
         where d.execute_blacklist is null
         """,
         (rs, rowNum) -> new BlacklistBackfillRow(rs.getString("id"), rs.getString("type"))
      );
      for (BlacklistBackfillRow row : rows) {
         String ruleIds = joinRuleIds(ExecuteBlacklistRules.defaultRuleIds(DatabaseType.from(row.type())));
         this.jdbcTemplate.update("update mcp_datasource set execute_blacklist = ? where id = ?", ruleIds, row.id());
         LOGGER.info("Seeded the default execute blacklist for datasource '{}': {}", row.id(), ruleIds);
      }
   }

   private void migrateLegacySchemaIfRequired() {
      if (!this.tableExists("mcp_datasource")) {
         return;
      }

      Set<String> datasourceColumns = this.tableColumns("mcp_datasource");
      Set<String> baseConfigColumns = this.tableColumns("mcp_base_jdbc_config");
      if (datasourceColumns.contains("base_config_id") && datasourceColumns.contains("username") && baseConfigColumns.contains("sid")) {
         return;
      }

      this.jdbcTemplate.execute("drop table if exists mcp_client_schema_mapping");
      this.jdbcTemplate.execute("drop table if exists mcp_client");
      this.jdbcTemplate.execute("drop table if exists mcp_datasource");
      this.jdbcTemplate.execute("drop table if exists mcp_base_jdbc_config");
   }

   private boolean tableExists(String tableName) {
      Integer count = this.jdbcTemplate.queryForObject(
         "select count(*) from sqlite_master where type = 'table' and name = ?",
         Integer.class,
         tableName
      );
      return count != null && count > 0;
   }

   private Set<String> tableColumns(String tableName) {
      return new HashSet<>(
         this.jdbcTemplate.query(
            "pragma table_info(" + tableName + ")",
            (rs, rowNum) -> rs.getString("name")
         )
      );
   }

   /**
    * Stored as a comma separated list of rule ids. A SQL {@code null} is kept as {@code null} so the
    * caller can tell "never configured" from "configured to block nothing" (an empty string).
    */
   private static List<String> parseRuleIds(String raw) {
      if (raw == null) {
         return null;
      }

      List<String> ruleIds = new ArrayList<>();
      for (String token : raw.split(",")) {
         String trimmed = token.trim();
         if (!trimmed.isEmpty()) {
            ruleIds.add(trimmed);
         }
      }
      return ruleIds;
   }

   private static String joinRuleIds(List<String> ruleIds) {
      return ruleIds == null ? null : String.join(",", ruleIds);
   }

   private record BlacklistBackfillRow(String id, String type) {
   }
}
