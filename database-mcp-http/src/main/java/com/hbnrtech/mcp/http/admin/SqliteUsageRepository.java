package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.http.admin.ConfigModels.DatasourceUsage;
import com.hbnrtech.mcp.http.admin.ConfigModels.ToolUsage;
import com.hbnrtech.mcp.http.admin.ConfigModels.UsageRecord;
import com.hbnrtech.mcp.tools.ToolInvocation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Keeps the per-call records that the hotspot ranking is built from, in the same SQLite file as the
 * configuration. The timestamps are fixed-width ISO-8601 UTC, so every window and ordering query
 * below can compare them as plain strings.
 */
@Repository
public class SqliteUsageRepository {
   private static final String INSERT_SQL = """
      insert into mcp_tool_invocation(datasource_id, tool_name, success, duration_ms, error_message, session_id, occurred_at)
      values (?, ?, ?, ?, ?, ?, ?)
      """;

   private final JdbcTemplate jdbcTemplate;

   public SqliteUsageRepository(JdbcTemplate configStoreJdbcTemplate) {
      this.jdbcTemplate = configStoreJdbcTemplate;
      this.initializeSchema();
   }

   private void initializeSchema() {
      this.jdbcTemplate.execute(
         """
         create table if not exists mcp_tool_invocation (
           id integer primary key autoincrement,
           datasource_id text not null,
           tool_name text not null,
           success integer not null,
           duration_ms integer not null,
           error_message text,
           session_id text,
           occurred_at text not null
         )
         """
      );
      this.jdbcTemplate.execute("create index if not exists idx_mcp_tool_invocation_datasource on mcp_tool_invocation(datasource_id, occurred_at)");
      this.jdbcTemplate.execute("create index if not exists idx_mcp_tool_invocation_time on mcp_tool_invocation(occurred_at)");
   }

   /**
    * One batch per flush, on the writer thread. The caller has already taken the records off the tool
    * execution thread, so a SQLite write lock here can never slow a tool call down.
    */
   public void insertAll(List<ToolInvocation> invocations) {
      if (invocations.isEmpty()) {
         return;
      }
      List<Object[]> batch = new ArrayList<>(invocations.size());
      for (ToolInvocation invocation : invocations) {
         batch.add(new Object[] {
            invocation.datasourceIdOrUnknown(),
            invocation.toolName(),
            invocation.success() ? 1 : 0,
            invocation.durationMillis(),
            invocation.errorMessage(),
            invocation.sessionId(),
            invocation.occurredAt()
         });
      }
      this.jdbcTemplate.batchUpdate(INSERT_SQL, batch);
   }

   /**
    * @param since inclusive lower bound, or {@code null} for the whole store
    */
   public List<DatasourceUsage> datasourceRanking(Instant since) {
      List<Object> args = new ArrayList<>();
      String where = since == null ? "" : "where occurred_at >= ?\n";
      if (since != null) {
         args.add(since.toString());
      }
      return this.jdbcTemplate.query(
         """
         select datasource_id,
                count(*) as call_count,
                sum(case when success = 1 then 1 else 0 end) as success_count,
                sum(case when success = 0 then 1 else 0 end) as failure_count,
                cast(round(avg(duration_ms)) as integer) as avg_duration_ms,
                max(duration_ms) as max_duration_ms,
                max(occurred_at) as last_seen
         from mcp_tool_invocation
         """ + where + """
         group by datasource_id
         order by call_count desc, datasource_id asc
         """,
         (rs, rowNum) -> new DatasourceUsage(
            rs.getString("datasource_id"),
            rs.getLong("call_count"),
            rs.getLong("success_count"),
            rs.getLong("failure_count"),
            rs.getLong("avg_duration_ms"),
            rs.getLong("max_duration_ms"),
            rs.getString("last_seen")
         ),
         args.toArray()
      );
   }

   public List<ToolUsage> toolRanking(Instant since) {
      List<Object> args = new ArrayList<>();
      String where = since == null ? "" : "where occurred_at >= ?\n";
      if (since != null) {
         args.add(since.toString());
      }
      return this.jdbcTemplate.query(
         """
         select tool_name,
                count(*) as call_count,
                sum(case when success = 1 then 1 else 0 end) as success_count,
                sum(case when success = 0 then 1 else 0 end) as failure_count,
                cast(round(avg(duration_ms)) as integer) as avg_duration_ms,
                max(duration_ms) as max_duration_ms,
                max(occurred_at) as last_seen
         from mcp_tool_invocation
         """ + where + """
         group by tool_name
         order by call_count desc, tool_name asc
         """,
         (rs, rowNum) -> new ToolUsage(
            rs.getString("tool_name"),
            rs.getLong("call_count"),
            rs.getLong("success_count"),
            rs.getLong("failure_count"),
            rs.getLong("avg_duration_ms"),
            rs.getLong("max_duration_ms"),
            rs.getString("last_seen")
         ),
         args.toArray()
      );
   }

   public long[] totals(Instant since) {
      List<Object> args = new ArrayList<>();
      String where = since == null ? "" : "where occurred_at >= ?\n";
      if (since != null) {
         args.add(since.toString());
      }
      List<long[]> rows = this.jdbcTemplate.query(
         """
         select count(*) as call_count,
                sum(case when success = 0 then 1 else 0 end) as failure_count
         from mcp_tool_invocation
         """ + where,
         (rs, rowNum) -> new long[] {rs.getLong("call_count"), rs.getLong("failure_count")},
         args.toArray()
      );
      return rows.isEmpty() ? new long[] {0L, 0L} : rows.get(0);
   }

   public long countRecords(String datasourceId, String toolName, String outcome, Instant since) {
      RecordFilter filter = this.recordFilter(datasourceId, toolName, outcome, since);
      Long count = this.jdbcTemplate.queryForObject(
         "select count(*) from mcp_tool_invocation " + filter.sql(),
         Long.class,
         filter.args()
      );
      return count == null ? 0L : count;
   }

   public List<UsageRecord> records(String datasourceId, String toolName, String outcome, Instant since, int limit, int offset) {
      RecordFilter filter = this.recordFilter(datasourceId, toolName, outcome, since);
      List<Object> args = new ArrayList<>(List.of(filter.args()));
      args.add(limit);
      args.add(offset);
      return this.jdbcTemplate.query(
         """
         select id, datasource_id, tool_name, success, duration_ms, error_message, session_id, occurred_at
         from mcp_tool_invocation
         """ + filter.sql() + """
         order by id desc
         limit ? offset ?
         """,
         (rs, rowNum) -> new UsageRecord(
            rs.getLong("id"),
            rs.getString("datasource_id"),
            rs.getString("tool_name"),
            rs.getInt("success") == 1,
            rs.getLong("duration_ms"),
            rs.getString("error_message"),
            rs.getString("session_id"),
            rs.getString("occurred_at")
         ),
         args.toArray()
      );
   }

   /** Distinct ids seen in the store, so the detail filter can offer them without a config round trip. */
   public List<String> knownDatasourceIds() {
      return this.jdbcTemplate.queryForList("select distinct datasource_id from mcp_tool_invocation order by datasource_id", String.class);
   }

   public List<String> knownToolNames() {
      return this.jdbcTemplate.queryForList("select distinct tool_name from mcp_tool_invocation order by tool_name", String.class);
   }

   public void deleteBefore(Instant cutoff) {
      this.jdbcTemplate.update("delete from mcp_tool_invocation where occurred_at < ?", cutoff.toString());
   }

   /**
    * Drops the oldest rows until at most {@code maxRows} remain. The subquery picks the id of the
    * newest kept row; on a shorter table that is the smallest id, so nothing is deleted.
    */
   public void trimToNewest(long maxRows) {
      this.jdbcTemplate.update(
         """
         delete from mcp_tool_invocation
         where id < (select min(kept.id) from (select id from mcp_tool_invocation order by id desc limit ?) as kept)
         """,
         maxRows
      );
   }

   public void deleteAll() {
      this.jdbcTemplate.update("delete from mcp_tool_invocation");
   }

   private RecordFilter recordFilter(String datasourceId, String toolName, String outcome, Instant since) {
      List<String> conditions = new ArrayList<>();
      List<Object> args = new ArrayList<>();
      if (datasourceId != null && !datasourceId.isBlank()) {
         conditions.add("datasource_id = ?");
         args.add(datasourceId);
      }
      if (toolName != null && !toolName.isBlank()) {
         conditions.add("tool_name = ?");
         args.add(toolName);
      }
      if ("success".equalsIgnoreCase(outcome)) {
         conditions.add("success = 1");
      } else if ("failure".equalsIgnoreCase(outcome)) {
         conditions.add("success = 0");
      }
      if (since != null) {
         conditions.add("occurred_at >= ?");
         args.add(since.toString());
      }
      return new RecordFilter(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions) + "\n", args.toArray());
   }

   private record RecordFilter(String sql, Object[] args) {
   }
}
