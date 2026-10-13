package com.hbnrtech.mcp.http.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hbnrtech.mcp.http.admin.ConfigModels.DatasourceUsage;
import com.hbnrtech.mcp.http.admin.ConfigModels.UsageRecord;
import com.hbnrtech.mcp.tools.ToolInvocation;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The ranking is built by SQL against string timestamps, so these tests pin the two things that
 * make that work: the window filter has to compare the same way the store orders, and the trim has
 * to keep the newest rows rather than the oldest.
 */
class SqliteUsageRepositoryTest {
   private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
   /** Comfortably outside the 7 day window and the 7 day retention cut used below. */
   private static final long TEN_DAYS_IN_MINUTES = 10 * 24 * 60L;

   @TempDir
   Path tempDir;

   private SqliteUsageRepository repository;

   @BeforeEach
   void prepareStoreOnATempFile() {
      DriverManagerDataSource dataSource = new DriverManagerDataSource();
      dataSource.setDriverClassName("org.sqlite.JDBC");
      dataSource.setUrl("jdbc:sqlite:" + this.tempDir.resolve("usage-test.db"));
      this.repository = new SqliteUsageRepository(new JdbcTemplate(dataSource));
   }

   @Test
   void ranksDatasourcesByCallCountWithSuccessAndFailureSplit() {
      this.repository.insertAll(List.of(
         call("busy-db", "db_query", true, 120, 0),
         call("busy-db", "db_execute", false, 900, 0),
         call("busy-db", "db_query", true, 80, 0),
         call("quiet-db", "db_list_tables", true, 5, 0)
      ));

      List<DatasourceUsage> ranking = this.repository.datasourceRanking(null);

      assertEquals(2, ranking.size());
      assertEquals("busy-db", ranking.get(0).datasourceId());
      assertEquals(3, ranking.get(0).callCount());
      assertEquals(2, ranking.get(0).successCount());
      assertEquals(1, ranking.get(0).failureCount());
      assertEquals(900, ranking.get(0).maxDurationMillis());
      assertEquals("quiet-db", ranking.get(1).datasourceId());
      assertEquals(1, ranking.get(1).callCount());
   }

   @Test
   void windowExcludesRecordsOlderThanTheLowerBound() {
      this.repository.insertAll(List.of(
         call("busy-db", "db_query", true, 10, TEN_DAYS_IN_MINUTES),
         call("busy-db", "db_query", true, 10, 0)
      ));

      Instant since = Instant.now().minus(7, ChronoUnit.DAYS);

      assertEquals(1, this.repository.datasourceRanking(since).get(0).callCount());
      assertEquals(2, this.repository.datasourceRanking(null).get(0).callCount());
      assertEquals(1, this.repository.toolRanking(since).get(0).callCount());
      assertEquals(1, this.repository.totals(since)[0]);
      assertEquals(1, this.repository.countRecords(null, null, null, since));
      assertEquals(1, this.repository.records(null, null, null, since, 5, 0).size());
   }

   @Test
   void filtersAndPagesTheDetailListNewestFirst() {
      this.repository.insertAll(List.of(
         call("a-db", "db_query", true, 10, 0),
         call("a-db", "db_execute", false, 20, 0),
         call("b-db", "db_query", true, 30, 0)
      ));

      assertEquals(3, this.repository.countRecords(null, null, null, null));
      assertEquals(2, this.repository.countRecords("a-db", null, null, null));
      assertEquals(1, this.repository.countRecords(null, null, "failure", null));
      assertEquals(2, this.repository.countRecords(null, null, "success", null));

      // Every filter has to survive the join with order by / limit, the outcome filter included.
      assertEquals("db_execute", this.repository.records(null, null, "failure", null, 5, 0).get(0).toolName());
      assertEquals(2, this.repository.records(null, null, "success", null, 5, 0).size());
      assertEquals(1, this.repository.records("a-db", "db_query", null, null, 5, 0).size());

      List<UsageRecord> firstPage = this.repository.records("a-db", null, null, null, 1, 0);
      assertEquals(1, firstPage.size());
      assertEquals("db_execute", firstPage.get(0).toolName());
      assertEquals("Unknown column", firstPage.get(0).errorMessage());

      List<UsageRecord> secondPage = this.repository.records("a-db", null, null, null, 1, 1);
      assertEquals("db_query", secondPage.get(0).toolName());
   }

   @Test
   void trimKeepsTheNewestRows() {
      for (int index = 0; index < 5; index++) {
         this.repository.insertAll(List.of(call("busy-db", "db_query", true, index, 0)));
      }

      this.repository.trimToNewest(2);

      List<UsageRecord> kept = this.repository.records(null, null, null, null, 10, 0);
      assertEquals(2, kept.size());
      assertEquals(4, kept.get(0).durationMillis());
      assertEquals(3, kept.get(1).durationMillis());
   }

   @Test
   void deleteBeforeDropsAgedRecords() {
      this.repository.insertAll(List.of(
         call("busy-db", "db_query", true, 10, TEN_DAYS_IN_MINUTES),
         call("busy-db", "db_query", true, 10, 0)
      ));

      this.repository.deleteBefore(Instant.now().minus(7, ChronoUnit.DAYS));

      assertEquals(1, this.repository.countRecords(null, null, null, null));
   }

   @Test
   void clearEmptiesTheStore() {
      this.repository.insertAll(List.of(call("busy-db", "db_query", true, 10, 0)));

      this.repository.deleteAll();

      assertEquals(0, this.repository.countRecords(null, null, null, null));
      assertTrue(this.repository.knownDatasourceIds().isEmpty());
   }

   private static ToolInvocation call(String datasourceId, String toolName, boolean success, long durationMillis, long minutesAgo) {
      return new ToolInvocation(
         datasourceId,
         toolName,
         success,
         durationMillis,
         success ? null : "Unknown column",
         "session-1",
         TIMESTAMP.format(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES))
      );
   }
}
