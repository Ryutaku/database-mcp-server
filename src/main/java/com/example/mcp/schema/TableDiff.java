package com.example.mcp.schema;

import java.util.ArrayList;
import java.util.List;

public class TableDiff {
   private final String objectName;
   private final DiffType type;
   private final String sourceDefinition;
   private final String targetDefinition;
   private final List<ColumnDiff> columnDiffs = new ArrayList<>();

   public TableDiff(String objectName, DiffType type, String sourceDefinition, String targetDefinition) {
      this.objectName = objectName;
      this.type = type;
      this.sourceDefinition = sourceDefinition;
      this.targetDefinition = targetDefinition;
   }

   public String objectName() {
      return this.objectName;
   }

   public DiffType type() {
      return this.type;
   }

   public String sourceDefinition() {
      return this.sourceDefinition;
   }

   public String targetDefinition() {
      return this.targetDefinition;
   }

   public List<ColumnDiff> columnDiffs() {
      return this.columnDiffs;
   }
}
