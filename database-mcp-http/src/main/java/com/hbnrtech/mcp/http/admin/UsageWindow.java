package com.hbnrtech.mcp.http.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * The time span the hotspot panel ranks. A window is a code on the wire, not an offset the caller
 * computes, so the panel and the store always agree on what "7 天" covers.
 */
public enum UsageWindow {
   TODAY("today"),
   LAST_7_DAYS("7d"),
   LAST_30_DAYS("30d"),
   ALL("all");

   private final String code;

   UsageWindow(String code) {
      this.code = code;
   }

   public String code() {
      return this.code;
   }

   /**
    * Inclusive lower bound, or {@code null} for {@link #ALL} which means the whole store.
    *
    * <p>{@link #TODAY} is the server's local day, the same convention the initial admin password
    * uses.
    */
   public Instant since() {
      return switch (this) {
         case TODAY -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
         case LAST_7_DAYS -> Instant.now().minus(Duration.ofDays(7));
         case LAST_30_DAYS -> Instant.now().minus(Duration.ofDays(30));
         case ALL -> null;
      };
   }

   /**
    * A missing or unknown window falls back to {@link #LAST_7_DAYS} rather than failing the request:
    * the panel should show a ranking, and the response echoes the window it used.
    */
   public static UsageWindow from(String value) {
      if (value == null || value.isBlank()) {
         return LAST_7_DAYS;
      }
      String normalized = value.trim().toLowerCase(Locale.ROOT);
      for (UsageWindow window : values()) {
         if (window.code.equals(normalized)) {
            return window;
         }
      }
      return LAST_7_DAYS;
   }
}
