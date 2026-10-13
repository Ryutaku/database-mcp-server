package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.http.config.DatabaseMcpHttpProperties;
import com.hbnrtech.mcp.tools.ToolInvocation;
import com.hbnrtech.mcp.tools.ToolInvocationListener;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

/**
 * Takes every tool call off the MCP execution thread and writes it to SQLite in batches.
 *
 * <p>The queue is the whole point. SQLite holds one write lock, so inserting on the call thread
 * would add a disk sync to every tool invocation. Here the call thread only offers to a bounded
 * queue; when that queue is full the record is dropped with one warning, because losing hotspot
 * statistics is better than stalling the database the client is asking.
 */
@Service
public class UsageRecordingService implements ToolInvocationListener, DisposableBean {
   private static final Logger LOGGER = LoggerFactory.getLogger(UsageRecordingService.class);
   private static final int MAX_BATCH_ROWS = 200;
   private static final long MIN_FLUSH_INTERVAL_MILLIS = 200L;
   private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);

   private final SqliteUsageRepository repository;
   private final DatabaseMcpHttpProperties.UsageStats config;
   private final BlockingQueue<ToolInvocation> queue;
   private final int queueCapacity;
   private final Thread writer;
   private final AtomicBoolean dropWarned = new AtomicBoolean();
   private volatile Instant lastCleanup = Instant.EPOCH;
   private volatile boolean running = true;

   public UsageRecordingService(SqliteUsageRepository repository, DatabaseMcpHttpProperties properties) {
      this.repository = repository;
      this.config = properties.getUsageStats();
      this.queueCapacity = Math.max(1, this.config.getQueueCapacity());
      this.queue = new LinkedBlockingQueue<>(this.queueCapacity);
      if (this.config.isEnabled()) {
         this.writer = new Thread(this::runLoop, "mcp-usage-writer");
         this.writer.setDaemon(true);
         this.writer.start();
         LOGGER.info("Tool call recording enabled: queue {}, flush every {}, retention {} days, cap {} rows",
            this.queueCapacity, this.config.getFlushInterval(), this.config.getRetentionDays(), this.config.getMaxRows());
      } else {
         this.writer = null;
         LOGGER.info("Tool call recording disabled; the hotspot panel stays empty");
      }
   }

   @Override
   public void record(ToolInvocation invocation) {
      if (this.writer == null) {
         return;
      }
      if (!this.queue.offer(invocation) && this.dropWarned.compareAndSet(false, true)) {
         LOGGER.warn("Usage queue is full ({} records), dropping further tool call records until it drains", this.queueCapacity);
      }
   }

   private void runLoop() {
      long flushIntervalMillis = Math.max(MIN_FLUSH_INTERVAL_MILLIS, this.config.getFlushInterval().toMillis());
      while (this.running) {
         List<ToolInvocation> batch = new ArrayList<>(MAX_BATCH_ROWS);
         try {
            ToolInvocation first = this.queue.poll(flushIntervalMillis, TimeUnit.MILLISECONDS);
            if (first != null) {
               batch.add(first);
               this.queue.drainTo(batch, MAX_BATCH_ROWS - 1);
            }
            this.flush(batch);
            this.cleanupIfDue();
         } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
         } catch (RuntimeException ex) {
            LOGGER.warn("Failed to persist {} tool call record(s): {}", batch.size(), ex.getMessage());
         }
      }
   }

   private void flush(List<ToolInvocation> batch) {
      if (batch.isEmpty()) {
         return;
      }
      this.repository.insertAll(batch);
      this.dropWarned.set(false);
   }

   /**
    * Retention runs on the writer thread, an hour at a time, so a restart cannot leave an aged store
    * untouched for a whole day the way a nightly schedule would.
    */
   private void cleanupIfDue() {
      Instant now = Instant.now();
      if (this.lastCleanup.isAfter(now.minus(CLEANUP_INTERVAL))) {
         return;
      }
      this.lastCleanup = now;
      try {
         if (this.config.getRetentionDays() > 0) {
            this.repository.deleteBefore(now.minus(Duration.ofDays(this.config.getRetentionDays())));
         }
         if (this.config.getMaxRows() > 0) {
            this.repository.trimToNewest(this.config.getMaxRows());
         }
      } catch (RuntimeException ex) {
         LOGGER.warn("Usage retention cleanup failed: {}", ex.getMessage());
      }
   }

   @Override
   public void destroy() {
      this.running = false;
      if (this.writer != null) {
         this.writer.interrupt();
      }
      List<ToolInvocation> pending = new ArrayList<>();
      this.queue.drainTo(pending);
      try {
         this.flush(pending);
      } catch (RuntimeException ex) {
         LOGGER.warn("Dropped {} queued tool call record(s) on shutdown: {}", pending.size(), ex.getMessage());
      }
   }
}
