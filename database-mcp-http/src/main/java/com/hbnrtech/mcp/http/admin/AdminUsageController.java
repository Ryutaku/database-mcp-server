package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.http.admin.ConfigModels.UsageFilters;
import com.hbnrtech.mcp.http.admin.ConfigModels.UsageRecordPage;
import com.hbnrtech.mcp.http.admin.ConfigModels.UsageStatsSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hotspot endpoints for the admin console. The admin password filter already covers the whole
 * {@code /admin/api} prefix, so these endpoints inherit it without extra wiring.
 */
@RestController
@RequestMapping("${database-mcp.http.admin-api-base-path:/admin/api}")
public class AdminUsageController {
   private final UsageStatsService usageStatsService;

   public AdminUsageController(UsageStatsService usageStatsService) {
      this.usageStatsService = usageStatsService;
   }

   /** The ranking: calls per datasource and per tool over the requested window. */
   @GetMapping("/usage-stats")
   public UsageStatsSummary stats(@RequestParam(name = "window", required = false) String window) {
      return this.usageStatsService.summary(window);
   }

   /** The recorded calls behind the ranking, newest first. */
   @GetMapping("/usage-records")
   public UsageRecordPage records(
      @RequestParam(name = "datasourceId", required = false) String datasourceId,
      @RequestParam(name = "tool", required = false) String tool,
      @RequestParam(name = "outcome", required = false) String outcome,
      @RequestParam(name = "window", required = false) String window,
      @RequestParam(name = "page", defaultValue = "1") int page,
      @RequestParam(name = "size", defaultValue = "20") int size
   ) {
      return this.usageStatsService.records(datasourceId, tool, outcome, window, page, size);
   }

   /** The filter values the detail list can offer. */
   @GetMapping("/usage-filters")
   public UsageFilters filters() {
      return this.usageStatsService.filters();
   }

   /** Drops every recorded call. The ranking rebuilds itself as calls come in. */
   @DeleteMapping("/usage-records")
   public ResponseEntity<Void> clear() {
      this.usageStatsService.clear();
      return ResponseEntity.noContent().build();
   }
}
