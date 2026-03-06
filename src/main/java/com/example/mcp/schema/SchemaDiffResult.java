package com.example.mcp.schema;

import java.util.ArrayList;
import java.util.List;

public class SchemaDiffResult {
   private final String sourceSchema;
   private final String targetSchema;
   private final List<TableDiff> tableDiffs = new ArrayList<>();
   private final List<IndexDiff> indexDiffs = new ArrayList<>();
   private final List<ConstraintDiff> constraintDiffs = new ArrayList<>();
   private final List<ViewDiff> viewDiffs = new ArrayList<>();
   private final List<RoutineDiff> routineDiffs = new ArrayList<>();
   private final List<SequenceDiff> sequenceDiffs = new ArrayList<>();
   private final List<String> syncScripts = new ArrayList<>();

   public SchemaDiffResult(String sourceSchema, String targetSchema) {
      this.sourceSchema = sourceSchema;
      this.targetSchema = targetSchema;
   }

   public String sourceSchema() {
      return this.sourceSchema;
   }

   public String targetSchema() {
      return this.targetSchema;
   }

   public List<TableDiff> tableDiffs() {
      return this.tableDiffs;
   }

   public List<IndexDiff> indexDiffs() {
      return this.indexDiffs;
   }

   public List<ConstraintDiff> constraintDiffs() {
      return this.constraintDiffs;
   }

   public List<ViewDiff> viewDiffs() {
      return this.viewDiffs;
   }

   public List<RoutineDiff> routineDiffs() {
      return this.routineDiffs;
   }

   public List<SequenceDiff> sequenceDiffs() {
      return this.sequenceDiffs;
   }

   public List<String> syncScripts() {
      return this.syncScripts;
   }

   public boolean hasDifferences() {
      return !this.tableDiffs.isEmpty()
         || !this.indexDiffs.isEmpty()
         || !this.constraintDiffs.isEmpty()
         || !this.viewDiffs.isEmpty()
         || !this.routineDiffs.isEmpty()
         || !this.sequenceDiffs.isEmpty();
   }
}
