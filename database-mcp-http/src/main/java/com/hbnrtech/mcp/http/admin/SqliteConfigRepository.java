package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.config.DatabaseType;
import com.hbnrtech.mcp.http.admin.ConfigModels.RuntimeSnapshot;
import com.hbnrtech.mcp.http.admin.ConfigModels.StoredBaseJdbcConfig;
import com.hbnrtech.mcp.http.admin.ConfigModels.StoredDatasource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SqliteConfigRepository {
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
         select id, base_config_id, username, password, schema_name
         from mcp_datasource
         order by id
         """,
         (rs, rowNum) -> new StoredDatasource(
            rs.getString("id"),
            rs.getString("base_config_id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("schema_name")
         )
      );

      return new RuntimeSnapshot(baseConfigs, datasources);
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
         insert into mcp_datasource(id, base_config_id, username, password, schema_name)
         values (?, ?, ?, ?, ?)
         on conflict(id) do update set
           base_config_id = excluded.base_config_id,
           username = excluded.username,
           password = excluded.password,
           schema_name = excluded.schema_name
         """,
         datasource.id(),
         datasource.baseConfigId(),
         datasource.username(),
         datasource.password(),
         datasource.schema()
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
           foreign key (base_config_id) references mcp_base_jdbc_config(id) on delete cascade
         )
         """
      );
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
}
