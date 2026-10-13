package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.http.admin.ConfigModels.UsageFilters;
import com.hbnrtech.mcp.http.admin.ConfigModels.UsageRecordPage;
import com.hbnrtech.mcp.http.admin.ConfigModels.UsageStatsSummary;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

/**
 * Reads the hotspot ranking and the call detail out of the store. Read only, apart from the explicit
 * clear the admin console asks for.
 */
@Service
public class UsageStatsService {
   private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
   private static final int MAX_PAGE_SIZE = 200;

   private final SqliteUsageRepository repository;

   public UsageStatsService(SqliteUsageRepository repository) {
      this.repository = repository;
   }

   public UsageStatsSummary summary(String windowParam) {
      UsageWindow window = UsageWindow.from(windowParam);
      Instant since = window.since();
      long[] totals = this.repository.totals(since);
      return new UsageStatsSummary(
         window.code(),
         GENERATED_AT.format(Instant.now()),
         totals[0],
         totals[1],
         this.repository.datasourceRanking(since),
         this.repository.toolRanking(since)
      );
   }

   public UsageRecordPage records(String datasourceId, String toolName, String outcome, String windowParam, int page, int size) {
      Instant since = UsageWindow.from(windowParam).since();
      int safePage = Math.max(1, page);
      int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
      long total = this.repository.countRecords(datasourceId, toolName, normalizeOutcome(outcome), since);
      return new UsageRecordPage(
         total,
         this.repository.records(datasourceId, toolName, normalizeOutcome(outcome), since, safeSize, (safePage - 1) * safeSize)
      );
   }

   public UsageFilters filters() {
      return new UsageFilters(this.repository.knownDatasourceIds(), this.repository.knownToolNames());
   }

   public void clear() {
      this.repository.deleteAll();
   }

   /** Anything that is not a known outcome means "do not filter", the same as the panel's default. */
   private static String normalizeOutcome(String outcome) {
      if ("success".equalsIgnoreCase(outcome) || "failure".equalsIgnoreCase(outcome)) {
         return outcome;
      }
      return null;
   }
}
