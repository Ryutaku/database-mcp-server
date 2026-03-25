package com.hbnrtech.mcp.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DatasourceRegistry implements AutoCloseable {
   private volatile Map<String, DatasourceContext> datasources;

   public DatasourceRegistry(Map<String, DatasourceContext> datasources) {
      this.datasources = immutableCopy(datasources);
   }

   public DatasourceContext getRequired(String datasourceId) {
      DatasourceContext context = this.datasources.get(datasourceId);
      if (context == null) {
         throw new IllegalArgumentException("Unknown datasourceId: " + datasourceId);
      }
      return context;
   }

   public Map<String, DatasourceContext> datasources() {
      return this.datasources;
   }

   public synchronized void replaceAll(Map<String, DatasourceContext> updatedDatasources) {
      Map<String, DatasourceContext> previous = this.datasources;
      Map<String, DatasourceContext> replacement = immutableCopy(updatedDatasources);
      this.datasources = replacement;
      for (Map.Entry<String, DatasourceContext> entry : previous.entrySet()) {
         DatasourceContext replacementContext = replacement.get(entry.getKey());
         if (replacementContext != entry.getValue()) {
            entry.getValue().connectionManager().close();
         }
      }
   }

   @Override
   public void close() {
      for (DatasourceContext context : this.datasources.values()) {
         context.connectionManager().close();
      }
   }

   private static Map<String, DatasourceContext> immutableCopy(Map<String, DatasourceContext> datasources) {
      if (datasources == null || datasources.isEmpty()) {
         return Collections.emptyMap();
      }
      return Collections.unmodifiableMap(new LinkedHashMap<>(datasources));
   }
}
