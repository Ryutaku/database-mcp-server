package com.hbnrtech.mcp.schema;

import java.util.Map;
import java.util.Objects;

public class SchemaDiffEngine {
   public SchemaDiffResult compare(SchemaSnapshot source, SchemaSnapshot target) {
      SchemaDiffResult result = new SchemaDiffResult(source.schemaName(), target.schemaName());
      this.compareTables(source.tables(), target.tables(), result);
      this.compareIndexes(source.indexes(), target.indexes(), result);
      this.compareConstraints(source.constraints(), target.constraints(), result);
      this.compareViews(source.views(), target.views(), result);
      this.compareRoutines(source.routines(), target.routines(), result);
      this.compareSequences(source.sequences(), target.sequences(), result);
      return result;
   }

   private void compareTables(Map<String, TableDef> sourceTables, Map<String, TableDef> targetTables, SchemaDiffResult result) {
      for (String tableName : sourceTables.keySet()) {
         if (!targetTables.containsKey(tableName)) {
            TableDef info = sourceTables.get(tableName);
            TableDiff diff = new TableDiff(tableName, DiffType.MISSING_IN_TARGET, info.ddl(), null);
            for (ColumnDef col : info.columns().values()) {
               diff.columnDiffs().add(new ColumnDiff(col.name(), DiffType.MISSING_IN_TARGET, col.dataType(), null, false, false));
            }
            result.tableDiffs().add(diff);
         } else {
            TableDef sourceInfo = sourceTables.get(tableName);
            TableDef targetInfo = targetTables.get(tableName);
            TableDiff diff = new TableDiff(tableName, DiffType.DIFFERENT, sourceInfo.ddl(), targetInfo.ddl());
            compareColumns(sourceInfo, targetInfo, diff);
            if (!diff.columnDiffs().isEmpty()) {
               result.tableDiffs().add(diff);
            }
         }
      }

      for (String tableName : targetTables.keySet()) {
         if (!sourceTables.containsKey(tableName)) {
            result.tableDiffs().add(new TableDiff(tableName, DiffType.MISSING_IN_SOURCE, null, targetTables.get(tableName).ddl()));
         }
      }
   }

   private void compareColumns(TableDef source, TableDef target, TableDiff diff) {
      for (String colName : source.columns().keySet()) {
         ColumnDef sourceCol = source.columns().get(colName);
         ColumnDef targetCol = target.columns().get(colName);
         if (targetCol == null) {
            diff.columnDiffs().add(new ColumnDiff(colName, DiffType.MISSING_IN_TARGET, sourceCol.dataType(), null, false, false));
         } else {
            boolean typeDiff = !Objects.equals(sourceCol.dataType(), targetCol.dataType());
            boolean nullableDiff = sourceCol.nullable() != targetCol.nullable();
            boolean defaultDiff = !Objects.equals(sourceCol.defaultValue(), targetCol.defaultValue());
            if (typeDiff || nullableDiff || defaultDiff) {
               diff.columnDiffs().add(new ColumnDiff(colName, DiffType.DIFFERENT, sourceCol.dataType(), targetCol.dataType(), nullableDiff, defaultDiff));
            }
         }
      }

      for (String colName : target.columns().keySet()) {
         if (!source.columns().containsKey(colName)) {
            diff.columnDiffs().add(new ColumnDiff(colName, DiffType.MISSING_IN_SOURCE, null, target.columns().get(colName).dataType(), false, false));
         }
      }
   }

   private void compareIndexes(Map<String, IndexDef> sourceIndexes, Map<String, IndexDef> targetIndexes, SchemaDiffResult result) {
      for (String indexName : sourceIndexes.keySet()) {
         if (!targetIndexes.containsKey(indexName)) {
            IndexDef info = sourceIndexes.get(indexName);
            result.indexDiffs().add(new IndexDiff(indexName, DiffType.MISSING_IN_TARGET, info.tableName(), info.definition(), null));
         } else {
            IndexDef sourceInfo = sourceIndexes.get(indexName);
            IndexDef targetInfo = targetIndexes.get(indexName);
            if (!normalize(sourceInfo.definition()).equals(normalize(targetInfo.definition()))) {
               result.indexDiffs().add(new IndexDiff(indexName, DiffType.DIFFERENT, sourceInfo.tableName(), sourceInfo.definition(), targetInfo.definition()));
            }
         }
      }

      for (String indexName : targetIndexes.keySet()) {
         if (!sourceIndexes.containsKey(indexName)) {
            IndexDef info = targetIndexes.get(indexName);
            result.indexDiffs().add(new IndexDiff(indexName, DiffType.MISSING_IN_SOURCE, info.tableName(), null, info.definition()));
         }
      }
   }

   private void compareConstraints(Map<String, ConstraintDef> sourceConstraints, Map<String, ConstraintDef> targetConstraints, SchemaDiffResult result) {
      for (String name : sourceConstraints.keySet()) {
         if (!targetConstraints.containsKey(name)) {
            ConstraintDef info = sourceConstraints.get(name);
            result.constraintDiffs().add(new ConstraintDiff(name, DiffType.MISSING_IN_TARGET, info.tableName(), info.constraintType(), info.definition(), null));
         } else {
            ConstraintDef sourceInfo = sourceConstraints.get(name);
            ConstraintDef targetInfo = targetConstraints.get(name);
            if (!Objects.equals(sourceInfo.definition(), targetInfo.definition())) {
               result.constraintDiffs().add(
                  new ConstraintDiff(name, DiffType.DIFFERENT, sourceInfo.tableName(), sourceInfo.constraintType(), sourceInfo.definition(), targetInfo.definition())
               );
            }
         }
      }

      for (String name : targetConstraints.keySet()) {
         if (!sourceConstraints.containsKey(name)) {
            ConstraintDef info = targetConstraints.get(name);
            result.constraintDiffs().add(new ConstraintDiff(name, DiffType.MISSING_IN_SOURCE, info.tableName(), info.constraintType(), null, info.definition()));
         }
      }
   }

   private void compareViews(Map<String, ViewDef> sourceViews, Map<String, ViewDef> targetViews, SchemaDiffResult result) {
      for (String name : sourceViews.keySet()) {
         if (!targetViews.containsKey(name)) {
            result.viewDiffs().add(new ViewDiff(name, DiffType.MISSING_IN_TARGET, sourceViews.get(name).definition(), null));
         } else if (!normalize(sourceViews.get(name).definition()).equals(normalize(targetViews.get(name).definition()))) {
            result.viewDiffs().add(new ViewDiff(name, DiffType.DIFFERENT, sourceViews.get(name).definition(), targetViews.get(name).definition()));
         }
      }

      for (String name : targetViews.keySet()) {
         if (!sourceViews.containsKey(name)) {
            result.viewDiffs().add(new ViewDiff(name, DiffType.MISSING_IN_SOURCE, null, targetViews.get(name).definition()));
         }
      }
   }

   private void compareRoutines(Map<String, RoutineDef> sourceRoutines, Map<String, RoutineDef> targetRoutines, SchemaDiffResult result) {
      for (String name : sourceRoutines.keySet()) {
         if (!targetRoutines.containsKey(name)) {
            result.routineDiffs().add(new RoutineDiff(name, DiffType.MISSING_IN_TARGET, sourceRoutines.get(name).definition(), null));
         } else if (!Objects.equals(sourceRoutines.get(name).definition(), targetRoutines.get(name).definition())) {
            result.routineDiffs().add(new RoutineDiff(name, DiffType.DIFFERENT, sourceRoutines.get(name).definition(), targetRoutines.get(name).definition()));
         }
      }

      for (String name : targetRoutines.keySet()) {
         if (!sourceRoutines.containsKey(name)) {
            result.routineDiffs().add(new RoutineDiff(name, DiffType.MISSING_IN_SOURCE, null, targetRoutines.get(name).definition()));
         }
      }
   }

   private void compareSequences(Map<String, SequenceDef> sourceSequences, Map<String, SequenceDef> targetSequences, SchemaDiffResult result) {
      for (String name : sourceSequences.keySet()) {
         if (!targetSequences.containsKey(name)) {
            result.sequenceDiffs().add(new SequenceDiff(name, DiffType.MISSING_IN_TARGET, sourceSequences.get(name).definition(), null));
         } else if (!Objects.equals(sourceSequences.get(name).definition(), targetSequences.get(name).definition())) {
            result.sequenceDiffs().add(new SequenceDiff(name, DiffType.DIFFERENT, sourceSequences.get(name).definition(), targetSequences.get(name).definition()));
         }
      }

      for (String name : targetSequences.keySet()) {
         if (!sourceSequences.containsKey(name)) {
            result.sequenceDiffs().add(new SequenceDiff(name, DiffType.MISSING_IN_SOURCE, null, targetSequences.get(name).definition()));
         }
      }
   }

   private String normalize(String value) {
      return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase();
   }
}
