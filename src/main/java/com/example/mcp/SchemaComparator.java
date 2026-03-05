package com.example.mcp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SchemaComparator {
   private final Connection connection;

   public SchemaComparator(Connection connection) {
      this.connection = connection;
   }

   public SchemaComparator.SchemaDiffResult compareSchemas(String sourceSchema, String targetSchema) throws SQLException {
      SchemaComparator.SchemaDiffResult result = new SchemaComparator.SchemaDiffResult(sourceSchema, targetSchema);
      this.compareTables(sourceSchema, targetSchema, result);
      this.compareIndexes(sourceSchema, targetSchema, result);
      this.compareConstraints(sourceSchema, targetSchema, result);
      this.compareViews(sourceSchema, targetSchema, result);
      this.compareFunctions(sourceSchema, targetSchema, result);
      this.compareSequences(sourceSchema, targetSchema, result);
      this.generateSyncScripts(result);
      return result;
   }

   private void compareTables(String sourceSchema, String targetSchema, SchemaComparator.SchemaDiffResult result) throws SQLException {
      Map<String, SchemaComparator.TableInfo> sourceTables = this.getTables(sourceSchema);
      Map<String, SchemaComparator.TableInfo> targetTables = this.getTables(targetSchema);

      for (String tableName : sourceTables.keySet()) {
         if (!targetTables.containsKey(tableName)) {
            SchemaComparator.TableInfo info = sourceTables.get(tableName);
            SchemaComparator.TableDiff diff = new SchemaComparator.TableDiff(tableName, SchemaComparator.DiffType.MISSING_IN_TARGET, info.definition, null);
            diff.columnDiffs.addAll(this.getAllColumnsAsDiffs(info.columns));
            result.tableDiffs.add(diff);
         } else {
            SchemaComparator.TableInfo sourceInfo = sourceTables.get(tableName);
            SchemaComparator.TableInfo targetInfo = targetTables.get(tableName);
            List<SchemaComparator.ColumnDiff> colDiffs = this.compareColumns(sourceInfo, targetInfo);
            if (!colDiffs.isEmpty()) {
               SchemaComparator.TableDiff diff = new SchemaComparator.TableDiff(
                  tableName, SchemaComparator.DiffType.DIFFERENT, sourceInfo.definition, targetInfo.definition
               );
               diff.columnDiffs.addAll(colDiffs);
               result.tableDiffs.add(diff);
            }
         }
      }

      for (String tableNamex : targetTables.keySet()) {
         if (!sourceTables.containsKey(tableNamex)) {
            SchemaComparator.TableInfo info = targetTables.get(tableNamex);
            SchemaComparator.TableDiff diff = new SchemaComparator.TableDiff(tableNamex, SchemaComparator.DiffType.MISSING_IN_SOURCE, null, info.definition);
            result.tableDiffs.add(diff);
         }
      }
   }

   private Map<String, SchemaComparator.TableInfo> getTables(String schema) throws SQLException {
      Map<String, SchemaComparator.TableInfo> tables = new LinkedHashMap<>();
      String sql = "SELECT\n    t.table_name\nFROM information_schema.tables t\nWHERE t.table_schema = ?\nAND t.table_type = 'BASE TABLE'\nORDER BY t.table_name\n";

      try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         ResultSet rs = stmt.executeQuery();

         while (rs.next()) {
            String tableName = rs.getString("table_name");
            String definition = this.buildTableDefinition(schema, tableName);
            SchemaComparator.TableInfo info = new SchemaComparator.TableInfo(tableName, schema, definition);
            info.columns = this.getColumns(schema, tableName);
            tables.put(tableName, info);
         }
      }

      return tables;
   }

   public String buildTableDefinition(String schema, String tableName) throws SQLException {
      StringBuilder sb = new StringBuilder();
      sb.append("CREATE TABLE ").append(this.quoteIdentifier(schema)).append(".").append(this.quoteIdentifier(tableName)).append(" (\n");
      Map<String, SchemaComparator.ColumnInfo> columns = this.getColumns(schema, tableName);
      List<String> colDefs = new ArrayList<>();

      for (SchemaComparator.ColumnInfo col : columns.values()) {
         StringBuilder colDef = new StringBuilder();
         colDef.append("    ").append(this.quoteIdentifier(col.name)).append(" ").append(col.dataType);
         if (!col.isNullable) {
            colDef.append(" NOT NULL");
         }

         if (col.defaultValue != null) {
            colDef.append(" DEFAULT ").append(col.defaultValue);
         }

         colDefs.add(colDef.toString());
      }

      sb.append(String.join(",\n", colDefs));
      sb.append("\n)");
      return sb.toString();
   }

   private Map<String, SchemaComparator.ColumnInfo> getColumns(String schema, String tableName) throws SQLException {
      Map<String, SchemaComparator.ColumnInfo> columns = new LinkedHashMap<>();
      String sql = "SELECT\n    c.column_name,\n    c.data_type,\n    c.character_maximum_length,\n    c.numeric_precision,\n    c.numeric_scale,\n    c.is_nullable,\n    c.column_default,\n    c.ordinal_position\nFROM information_schema.columns c\nWHERE c.table_schema = ? AND c.table_name = ?\nORDER BY c.ordinal_position\n";

      try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         stmt.setString(2, tableName);
         ResultSet rs = stmt.executeQuery();

         while (rs.next()) {
            SchemaComparator.ColumnInfo col = new SchemaComparator.ColumnInfo();
            col.name = rs.getString("column_name");
            col.dataType = this.buildDataType(rs);
            col.isNullable = "YES".equals(rs.getString("is_nullable"));
            col.defaultValue = rs.getString("column_default");
            col.ordinalPosition = rs.getInt("ordinal_position");
            columns.put(col.name, col);
         }
      }

      return columns;
   }

   private String buildDataType(ResultSet rs) throws SQLException {
      String type = rs.getString("data_type");
      Integer charMaxLen = rs.getObject("character_maximum_length", Integer.class);
      Integer numPrecision = rs.getObject("numeric_precision", Integer.class);
      Integer numScale = rs.getObject("numeric_scale", Integer.class);
      if (charMaxLen != null) {
         return type + "(" + charMaxLen + ")";
      } else if (numPrecision != null && numScale != null && numScale > 0) {
         return type + "(" + numPrecision + "," + numScale + ")";
      } else {
         return numPrecision != null ? type + "(" + numPrecision + ")" : type;
      }
   }

   private List<SchemaComparator.ColumnDiff> compareColumns(SchemaComparator.TableInfo source, SchemaComparator.TableInfo target) {
      List<SchemaComparator.ColumnDiff> diffs = new ArrayList<>();

      for (String colName : source.columns.keySet()) {
         SchemaComparator.ColumnInfo sourceCol = source.columns.get(colName);
         SchemaComparator.ColumnInfo targetCol = target.columns.get(colName);
         if (targetCol == null) {
            diffs.add(new SchemaComparator.ColumnDiff(colName, SchemaComparator.DiffType.MISSING_IN_TARGET, sourceCol.dataType, null, false, false));
         } else {
            boolean typeDiff = !Objects.equals(sourceCol.dataType, targetCol.dataType);
            boolean nullableDiff = sourceCol.isNullable != targetCol.isNullable;
            boolean defaultDiff = !Objects.equals(sourceCol.defaultValue, targetCol.defaultValue);
            if (typeDiff || nullableDiff || defaultDiff) {
               diffs.add(
                  new SchemaComparator.ColumnDiff(
                     colName, SchemaComparator.DiffType.DIFFERENT, sourceCol.dataType, targetCol.dataType, nullableDiff, defaultDiff
                  )
               );
            }
         }
      }

      for (String colNamex : target.columns.keySet()) {
         if (!source.columns.containsKey(colNamex)) {
            SchemaComparator.ColumnInfo targetCol = target.columns.get(colNamex);
            diffs.add(new SchemaComparator.ColumnDiff(colNamex, SchemaComparator.DiffType.MISSING_IN_SOURCE, null, targetCol.dataType, false, false));
         }
      }

      return diffs;
   }

   private List<SchemaComparator.ColumnDiff> getAllColumnsAsDiffs(Map<String, SchemaComparator.ColumnInfo> columns) {
      List<SchemaComparator.ColumnDiff> diffs = new ArrayList<>();

      for (SchemaComparator.ColumnInfo col : columns.values()) {
         diffs.add(new SchemaComparator.ColumnDiff(col.name, SchemaComparator.DiffType.MISSING_IN_TARGET, col.dataType, null, false, false));
      }

      return diffs;
   }

   private void compareIndexes(String sourceSchema, String targetSchema, SchemaComparator.SchemaDiffResult result) throws SQLException {
      Map<String, SchemaComparator.IndexInfo> sourceIndexes = this.getIndexes(sourceSchema);
      Map<String, SchemaComparator.IndexInfo> targetIndexes = this.getIndexes(targetSchema);

      for (String indexName : sourceIndexes.keySet()) {
         if (!targetIndexes.containsKey(indexName)) {
            SchemaComparator.IndexInfo info = sourceIndexes.get(indexName);
            result.indexDiffs
               .add(new SchemaComparator.IndexDiff(indexName, SchemaComparator.DiffType.MISSING_IN_TARGET, info.tableName, info.definition, null));
         } else {
            SchemaComparator.IndexInfo sourceInfo = sourceIndexes.get(indexName);
            SchemaComparator.IndexInfo targetInfo = targetIndexes.get(indexName);
            if (!this.normalizeIndexDef(sourceInfo.definition).equals(this.normalizeIndexDef(targetInfo.definition))) {
               result.indexDiffs
                  .add(
                     new SchemaComparator.IndexDiff(
                        indexName, SchemaComparator.DiffType.DIFFERENT, sourceInfo.tableName, sourceInfo.definition, targetInfo.definition
                     )
                  );
            }
         }
      }

      for (String indexNamex : targetIndexes.keySet()) {
         if (!sourceIndexes.containsKey(indexNamex)) {
            SchemaComparator.IndexInfo info = targetIndexes.get(indexNamex);
            result.indexDiffs
               .add(new SchemaComparator.IndexDiff(indexNamex, SchemaComparator.DiffType.MISSING_IN_SOURCE, info.tableName, null, info.definition));
         }
      }
   }

   private Map<String, SchemaComparator.IndexInfo> getIndexes(String schema) throws SQLException {
      Map<String, SchemaComparator.IndexInfo> indexes = new LinkedHashMap<>();
      String sql = "SELECT\n    indexname,\n    tablename,\n    indexdef\nFROM pg_indexes\nWHERE schemaname = ?\nORDER BY indexname\n";

      try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         ResultSet rs = stmt.executeQuery();

         while (rs.next()) {
            SchemaComparator.IndexInfo info = new SchemaComparator.IndexInfo();
            info.name = rs.getString("indexname");
            info.tableName = rs.getString("tablename");
            info.definition = rs.getString("indexdef");
            indexes.put(info.name, info);
         }
      }

      return indexes;
   }

   private String normalizeIndexDef(String def) {
      return def == null ? "" : def.replaceAll("\\s+", " ").trim().toLowerCase();
   }

   private void compareConstraints(String sourceSchema, String targetSchema, SchemaComparator.SchemaDiffResult result) throws SQLException {
      Map<String, SchemaComparator.ConstraintInfo> sourceConstraints = this.getConstraints(sourceSchema);
      Map<String, SchemaComparator.ConstraintInfo> targetConstraints = this.getConstraints(targetSchema);

      for (String constraintName : sourceConstraints.keySet()) {
         if (!targetConstraints.containsKey(constraintName)) {
            SchemaComparator.ConstraintInfo info = sourceConstraints.get(constraintName);
            result.constraintDiffs
               .add(
                  new SchemaComparator.ConstraintDiff(
                     constraintName, SchemaComparator.DiffType.MISSING_IN_TARGET, info.tableName, info.constraintType, info.definition, null
                  )
               );
         } else {
            SchemaComparator.ConstraintInfo sourceInfo = sourceConstraints.get(constraintName);
            SchemaComparator.ConstraintInfo targetInfo = targetConstraints.get(constraintName);
            if (!sourceInfo.definition.equals(targetInfo.definition)) {
               result.constraintDiffs
                  .add(
                     new SchemaComparator.ConstraintDiff(
                        constraintName,
                        SchemaComparator.DiffType.DIFFERENT,
                        sourceInfo.tableName,
                        sourceInfo.constraintType,
                        sourceInfo.definition,
                        targetInfo.definition
                     )
                  );
            }
         }
      }

      for (String constraintNamex : targetConstraints.keySet()) {
         if (!sourceConstraints.containsKey(constraintNamex)) {
            SchemaComparator.ConstraintInfo info = targetConstraints.get(constraintNamex);
            result.constraintDiffs
               .add(
                  new SchemaComparator.ConstraintDiff(
                     constraintNamex, SchemaComparator.DiffType.MISSING_IN_SOURCE, info.tableName, info.constraintType, null, info.definition
                  )
               );
         }
      }
   }

   private Map<String, SchemaComparator.ConstraintInfo> getConstraints(String schema) throws SQLException {
      Map<String, SchemaComparator.ConstraintInfo> constraints = new LinkedHashMap<>();
      String sql = "SELECT\n    tc.constraint_name,\n    tc.table_name,\n    tc.constraint_type,\n    CASE tc.constraint_type\n        WHEN 'PRIMARY KEY' THEN\n            (SELECT 'PRIMARY KEY (' || string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) || ')'\n             FROM information_schema.key_column_usage kcu\n             WHERE kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema)\n        WHEN 'UNIQUE' THEN\n            (SELECT 'UNIQUE (' || string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) || ')'\n             FROM information_schema.key_column_usage kcu\n             WHERE kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema)\n        WHEN 'FOREIGN KEY' THEN\n            (SELECT 'FOREIGN KEY (' || string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) || ') REFERENCES ' ||\n                    ccu.table_name || ' (' || ccu.column_name || ')'\n             FROM information_schema.key_column_usage kcu\n             JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name\n             WHERE kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema)\n        ELSE ''\n    END as definition\nFROM information_schema.table_constraints tc\nWHERE tc.table_schema = ?\nAND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE', 'FOREIGN KEY')\nORDER BY tc.constraint_name\n";

      try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         ResultSet rs = stmt.executeQuery();

         while (rs.next()) {
            SchemaComparator.ConstraintInfo info = new SchemaComparator.ConstraintInfo();
            info.name = rs.getString("constraint_name");
            info.tableName = rs.getString("table_name");
            info.constraintType = rs.getString("constraint_type");
            info.definition = rs.getString("definition");
            constraints.put(info.name, info);
         }
      }

      return constraints;
   }

   private void compareViews(String sourceSchema, String targetSchema, SchemaComparator.SchemaDiffResult result) throws SQLException {
      Map<String, String> sourceViews = this.getViews(sourceSchema);
      Map<String, String> targetViews = this.getViews(targetSchema);

      for (String viewName : sourceViews.keySet()) {
         if (!targetViews.containsKey(viewName)) {
            result.viewDiffs.add(new SchemaComparator.ViewDiff(viewName, SchemaComparator.DiffType.MISSING_IN_TARGET, sourceViews.get(viewName), null));
         } else {
            String sourceDef = sourceViews.get(viewName);
            String targetDef = targetViews.get(viewName);
            if (!this.normalizeViewDef(sourceDef).equals(this.normalizeViewDef(targetDef))) {
               result.viewDiffs.add(new SchemaComparator.ViewDiff(viewName, SchemaComparator.DiffType.DIFFERENT, sourceDef, targetDef));
            }
         }
      }

      for (String viewNamex : targetViews.keySet()) {
         if (!sourceViews.containsKey(viewNamex)) {
            result.viewDiffs.add(new SchemaComparator.ViewDiff(viewNamex, SchemaComparator.DiffType.MISSING_IN_SOURCE, null, targetViews.get(viewNamex)));
         }
      }
   }

   private Map<String, String> getViews(String schema) throws SQLException {
      Map<String, String> views = new LinkedHashMap<>();
      String sql = "SELECT\n    viewname,\n    definition\nFROM pg_views\nWHERE schemaname = ?\nORDER BY viewname\n";

      try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         ResultSet rs = stmt.executeQuery();

         while (rs.next()) {
            views.put(rs.getString("viewname"), rs.getString("definition"));
         }
      }

      return views;
   }

   private String normalizeViewDef(String def) {
      return def == null ? "" : def.replaceAll("\\s+", " ").trim().toLowerCase();
   }

   private void compareFunctions(String sourceSchema, String targetSchema, SchemaComparator.SchemaDiffResult result) throws SQLException {
      Map<String, String> sourceFunctions = this.getFunctions(sourceSchema);
      Map<String, String> targetFunctions = this.getFunctions(targetSchema);

      for (String funcName : sourceFunctions.keySet()) {
         if (!targetFunctions.containsKey(funcName)) {
            result.functionDiffs
               .add(new SchemaComparator.FunctionDiff(funcName, SchemaComparator.DiffType.MISSING_IN_TARGET, sourceFunctions.get(funcName), null));
         } else {
            String sourceDef = sourceFunctions.get(funcName);
            String targetDef = targetFunctions.get(funcName);
            if (!sourceDef.equals(targetDef)) {
               result.functionDiffs.add(new SchemaComparator.FunctionDiff(funcName, SchemaComparator.DiffType.DIFFERENT, sourceDef, targetDef));
            }
         }
      }

      for (String funcNamex : targetFunctions.keySet()) {
         if (!sourceFunctions.containsKey(funcNamex)) {
            result.functionDiffs
               .add(new SchemaComparator.FunctionDiff(funcNamex, SchemaComparator.DiffType.MISSING_IN_SOURCE, null, targetFunctions.get(funcNamex)));
         }
      }
   }

   private Map<String, String> getFunctions(String schema) throws SQLException {
      Map<String, String> functions = new LinkedHashMap<>();
      String sql = "SELECT\n    p.proname as func_name,\n    pg_get_function_identity_arguments(p.oid) as func_args\nFROM pg_proc p\nJOIN pg_namespace n ON p.pronamespace = n.oid\nWHERE n.nspname = ?\nAND p.prokind = 'f'\nORDER BY p.proname\n";

      try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         ResultSet rs = stmt.executeQuery();

         while (rs.next()) {
            String funcName = rs.getString("func_name");
            String funcArgs = rs.getString("func_args");
            String signature = funcName + "(" + (funcArgs != null ? funcArgs : "") + ")";
            functions.put(signature, "FUNCTION " + signature);
         }
      }

      return functions;
   }

   private void compareSequences(String sourceSchema, String targetSchema, SchemaComparator.SchemaDiffResult result) throws SQLException {
      Map<String, String> sourceSequences = this.getSequences(sourceSchema);
      Map<String, String> targetSequences = this.getSequences(targetSchema);

      for (String seqName : sourceSequences.keySet()) {
         if (!targetSequences.containsKey(seqName)) {
            result.sequenceDiffs
               .add(new SchemaComparator.SequenceDiff(seqName, SchemaComparator.DiffType.MISSING_IN_TARGET, sourceSequences.get(seqName), null));
         } else {
            String sourceDef = sourceSequences.get(seqName);
            String targetDef = targetSequences.get(seqName);
            if (!sourceDef.equals(targetDef)) {
               result.sequenceDiffs.add(new SchemaComparator.SequenceDiff(seqName, SchemaComparator.DiffType.DIFFERENT, sourceDef, targetDef));
            }
         }
      }

      for (String seqNamex : targetSequences.keySet()) {
         if (!sourceSequences.containsKey(seqNamex)) {
            result.sequenceDiffs
               .add(new SchemaComparator.SequenceDiff(seqNamex, SchemaComparator.DiffType.MISSING_IN_SOURCE, null, targetSequences.get(seqNamex)));
         }
      }
   }

   private Map<String, String> getSequences(String schema) throws SQLException {
      Map<String, String> sequences = new LinkedHashMap<>();
      String sql = "SELECT\n    sequence_name,\n    'CREATE SEQUENCE ' || sequence_name ||\n    ' START WITH ' || start_value ||\n    ' INCREMENT BY ' || increment_by ||\n    ' MINVALUE ' || min_value ||\n    ' MAXVALUE ' || max_value ||\n    CASE WHEN cycle = 'YES' THEN ' CYCLE' ELSE ' NO CYCLE' END as definition\nFROM information_schema.sequences\nWHERE sequence_schema = ?\nORDER BY sequence_name\n";

      try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
         stmt.setString(1, schema);
         ResultSet rs = stmt.executeQuery();

         while (rs.next()) {
            sequences.put(rs.getString("sequence_name"), rs.getString("definition"));
         }
      }

      return sequences;
   }

   private void generateSyncScripts(SchemaComparator.SchemaDiffResult result) {
      String targetSchema = result.targetSchema;

      for (SchemaComparator.TableDiff diff : result.tableDiffs) {
         switch (diff.type) {
            case MISSING_IN_TARGET:
               if (diff.sourceDefinition != null) {
                  result.syncScripts.add("-- 创建表: " + diff.objectName);
                  result.syncScripts.add(diff.sourceDefinition.replace(result.sourceSchema + ".", targetSchema + ".") + ";");
               }
               break;
            case MISSING_IN_SOURCE:
               result.syncScripts.add("-- 删除多余表: " + diff.objectName);
               result.syncScripts.add("DROP TABLE " + this.quoteIdentifier(targetSchema) + "." + this.quoteIdentifier(diff.objectName) + ";");
               break;
            case DIFFERENT:
               for (SchemaComparator.ColumnDiff colDiff : diff.columnDiffs) {
                  String fullTableName = this.quoteIdentifier(targetSchema) + "." + this.quoteIdentifier(diff.objectName);
                  switch (colDiff.type) {
                     case MISSING_IN_TARGET:
                        result.syncScripts.add("-- 添加列: " + diff.objectName + "." + colDiff.columnName);
                        result.syncScripts
                           .add("ALTER TABLE " + fullTableName + " ADD COLUMN " + this.quoteIdentifier(colDiff.columnName) + " " + colDiff.sourceType + ";");
                        break;
                     case MISSING_IN_SOURCE:
                        result.syncScripts.add("-- 删除多余列: " + diff.objectName + "." + colDiff.columnName);
                        result.syncScripts.add("ALTER TABLE " + fullTableName + " DROP COLUMN " + this.quoteIdentifier(colDiff.columnName) + ";");
                        break;
                     case DIFFERENT:
                        if (!Objects.equals(colDiff.sourceType, colDiff.targetType)) {
                           result.syncScripts.add("-- 修改列类型: " + diff.objectName + "." + colDiff.columnName);
                           result.syncScripts
                              .add(
                                 "ALTER TABLE "
                                    + fullTableName
                                    + " ALTER COLUMN "
                                    + this.quoteIdentifier(colDiff.columnName)
                                    + " TYPE "
                                    + colDiff.sourceType
                                    + ";"
                              );
                        }
                  }
               }
         }
      }

      for (SchemaComparator.IndexDiff diff : result.indexDiffs) {
         String fullIndexName = this.quoteIdentifier(targetSchema) + "." + this.quoteIdentifier(diff.objectName);
         switch (diff.type) {
            case MISSING_IN_TARGET:
               if (diff.sourceDefinition != null) {
                  result.syncScripts.add("-- 创建索引: " + diff.objectName);
                  result.syncScripts.add(diff.sourceDefinition.replace(result.sourceSchema + ".", targetSchema + ".") + ";");
               }
               break;
            case MISSING_IN_SOURCE:
               result.syncScripts.add("-- 删除多余索引: " + diff.objectName);
               result.syncScripts.add("DROP INDEX " + fullIndexName + ";");
               break;
            case DIFFERENT:
               result.syncScripts.add("-- 重建索引: " + diff.objectName);
               result.syncScripts.add("DROP INDEX " + fullIndexName + ";");
               result.syncScripts.add(diff.sourceDefinition.replace(result.sourceSchema + ".", targetSchema + ".") + ";");
         }
      }

      for (SchemaComparator.ConstraintDiff diff : result.constraintDiffs) {
         String fullTableName = this.quoteIdentifier(targetSchema) + "." + this.quoteIdentifier(diff.tableName);
         switch (diff.type) {
            case MISSING_IN_TARGET:
               if (diff.sourceDefinition != null) {
                  result.syncScripts.add("-- 添加约束: " + diff.objectName);
                  result.syncScripts
                     .add("ALTER TABLE " + fullTableName + " ADD CONSTRAINT " + this.quoteIdentifier(diff.objectName) + " " + diff.sourceDefinition + ";");
               }
               break;
            case MISSING_IN_SOURCE:
               result.syncScripts.add("-- 删除多余约束: " + diff.objectName);
               result.syncScripts.add("ALTER TABLE " + fullTableName + " DROP CONSTRAINT " + this.quoteIdentifier(diff.objectName) + ";");
               break;
            case DIFFERENT:
               result.syncScripts.add("-- 修改约束: " + diff.objectName);
               result.syncScripts.add("ALTER TABLE " + fullTableName + " DROP CONSTRAINT " + this.quoteIdentifier(diff.objectName) + ";");
               result.syncScripts
                  .add("ALTER TABLE " + fullTableName + " ADD CONSTRAINT " + this.quoteIdentifier(diff.objectName) + " " + diff.sourceDefinition + ";");
         }
      }

      for (SchemaComparator.ViewDiff diff : result.viewDiffs) {
         String fullViewName = this.quoteIdentifier(targetSchema) + "." + this.quoteIdentifier(diff.objectName);
         switch (diff.type) {
            case MISSING_IN_TARGET:
               if (diff.sourceDefinition != null) {
                  result.syncScripts.add("-- 创建视图: " + diff.objectName);
                  result.syncScripts.add("CREATE OR REPLACE VIEW " + fullViewName + " AS " + diff.sourceDefinition + ";");
               }
               break;
            case MISSING_IN_SOURCE:
               result.syncScripts.add("-- 删除多余视图: " + diff.objectName);
               result.syncScripts.add("DROP VIEW " + fullViewName + ";");
               break;
            case DIFFERENT:
               result.syncScripts.add("-- 更新视图: " + diff.objectName);
               result.syncScripts.add("CREATE OR REPLACE VIEW " + fullViewName + " AS " + diff.sourceDefinition + ";");
         }
      }

      for (SchemaComparator.FunctionDiff diff : result.functionDiffs) {
         switch (diff.type) {
            case MISSING_IN_TARGET:
               if (diff.sourceDefinition != null) {
                  result.syncScripts.add("-- 创建函数: " + diff.objectName);
                  result.syncScripts.add(diff.sourceDefinition.replace(result.sourceSchema + ".", targetSchema + ".") + ";");
               }
               break;
            case MISSING_IN_SOURCE:
               result.syncScripts.add("-- 删除多余函数: " + diff.objectName);
               result.syncScripts.add("DROP FUNCTION " + this.quoteIdentifier(targetSchema) + "." + diff.objectName + ";");
               break;
            case DIFFERENT:
               result.syncScripts.add("-- 更新函数: " + diff.objectName);
               result.syncScripts.add(diff.sourceDefinition.replace(result.sourceSchema + ".", targetSchema + ".") + ";");
         }
      }

      for (SchemaComparator.SequenceDiff diff : result.sequenceDiffs) {
         String fullSeqName = this.quoteIdentifier(targetSchema) + "." + this.quoteIdentifier(diff.objectName);
         switch (diff.type) {
            case MISSING_IN_TARGET:
               if (diff.sourceDefinition != null) {
                  result.syncScripts.add("-- 创建序列: " + diff.objectName);
                  result.syncScripts.add(diff.sourceDefinition.replace(" " + diff.objectName + " ", " " + fullSeqName + " ") + ";");
               }
               break;
            case MISSING_IN_SOURCE:
               result.syncScripts.add("-- 删除多余序列: " + diff.objectName);
               result.syncScripts.add("DROP SEQUENCE " + fullSeqName + ";");
               break;
            case DIFFERENT:
               result.syncScripts.add("-- 注意: 序列定义不同: " + diff.objectName);
               result.syncScripts.add("-- 源: " + diff.sourceDefinition);
               result.syncScripts.add("-- 目标: " + diff.targetDefinition);
         }
      }
   }

   private String quoteIdentifier(String identifier) {
      return "\"" + identifier.replace("\"", "\"\"") + "\"";
   }

   public static class ColumnDiff {
      public final String columnName;
      public final SchemaComparator.DiffType type;
      public final String sourceType;
      public final String targetType;
      public final boolean nullableDiff;
      public final boolean defaultDiff;

      public ColumnDiff(String columnName, SchemaComparator.DiffType type, String sourceType, String targetType, boolean nullableDiff, boolean defaultDiff) {
         this.columnName = columnName;
         this.type = type;
         this.sourceType = sourceType;
         this.targetType = targetType;
         this.nullableDiff = nullableDiff;
         this.defaultDiff = defaultDiff;
      }
   }

   private static class ColumnInfo {
      String name;
      String dataType;
      boolean isNullable;
      String defaultValue;
      int ordinalPosition;
   }

   public static class ConstraintDiff extends SchemaComparator.ObjectDiff {
      public final String tableName;
      public final String constraintType;
      public final String sourceDefinition;
      public final String targetDefinition;

      public ConstraintDiff(String constraintName, SchemaComparator.DiffType type, String tableName, String constraintType, String sourceDef, String targetDef) {
         super(constraintName, type);
         this.tableName = tableName;
         this.constraintType = constraintType;
         this.sourceDefinition = sourceDef;
         this.targetDefinition = targetDef;
      }
   }

   private static class ConstraintInfo {
      String name;
      String tableName;
      String constraintType;
      String definition;
   }

   public static enum DiffType {
      MISSING_IN_TARGET,
      MISSING_IN_SOURCE,
      DIFFERENT;
   }

   public static class FunctionDiff extends SchemaComparator.ObjectDiff {
      public final String sourceDefinition;
      public final String targetDefinition;

      public FunctionDiff(String functionName, SchemaComparator.DiffType type, String sourceDef, String targetDef) {
         super(functionName, type);
         this.sourceDefinition = sourceDef;
         this.targetDefinition = targetDef;
      }
   }

   public static class IndexDiff extends SchemaComparator.ObjectDiff {
      public final String tableName;
      public final String sourceDefinition;
      public final String targetDefinition;

      public IndexDiff(String indexName, SchemaComparator.DiffType type, String tableName, String sourceDef, String targetDef) {
         super(indexName, type);
         this.tableName = tableName;
         this.sourceDefinition = sourceDef;
         this.targetDefinition = targetDef;
      }
   }

   private static class IndexInfo {
      String name;
      String tableName;
      String definition;
   }

   public abstract static class ObjectDiff {
      public final String objectName;
      public final SchemaComparator.DiffType type;

      public ObjectDiff(String objectName, SchemaComparator.DiffType type) {
         this.objectName = objectName;
         this.type = type;
      }
   }

   public static class SchemaDiffResult {
      public final String sourceSchema;
      public final String targetSchema;
      public final List<SchemaComparator.TableDiff> tableDiffs = new ArrayList<>();
      public final List<SchemaComparator.IndexDiff> indexDiffs = new ArrayList<>();
      public final List<SchemaComparator.ConstraintDiff> constraintDiffs = new ArrayList<>();
      public final List<SchemaComparator.ViewDiff> viewDiffs = new ArrayList<>();
      public final List<SchemaComparator.FunctionDiff> functionDiffs = new ArrayList<>();
      public final List<SchemaComparator.SequenceDiff> sequenceDiffs = new ArrayList<>();
      public final List<String> syncScripts = new ArrayList<>();

      public SchemaDiffResult(String sourceSchema, String targetSchema) {
         this.sourceSchema = sourceSchema;
         this.targetSchema = targetSchema;
      }

      public boolean hasDifferences() {
         return !this.tableDiffs.isEmpty()
            || !this.indexDiffs.isEmpty()
            || !this.constraintDiffs.isEmpty()
            || !this.viewDiffs.isEmpty()
            || !this.functionDiffs.isEmpty()
            || !this.sequenceDiffs.isEmpty();
      }
   }

   public static class SequenceDiff extends SchemaComparator.ObjectDiff {
      public final String sourceDefinition;
      public final String targetDefinition;

      public SequenceDiff(String sequenceName, SchemaComparator.DiffType type, String sourceDef, String targetDef) {
         super(sequenceName, type);
         this.sourceDefinition = sourceDef;
         this.targetDefinition = targetDef;
      }
   }

   public static class TableDiff extends SchemaComparator.ObjectDiff {
      public final List<SchemaComparator.ColumnDiff> columnDiffs = new ArrayList<>();
      public final String sourceDefinition;
      public final String targetDefinition;

      public TableDiff(String tableName, SchemaComparator.DiffType type, String sourceDef, String targetDef) {
         super(tableName, type);
         this.sourceDefinition = sourceDef;
         this.targetDefinition = targetDef;
      }
   }

   private static class TableInfo {
      final String name;
      final String schema;
      final String definition;
      Map<String, SchemaComparator.ColumnInfo> columns = new LinkedHashMap<>();

      TableInfo(String name, String schema, String definition) {
         this.name = name;
         this.schema = schema;
         this.definition = definition;
      }
   }

   public static class ViewDiff extends SchemaComparator.ObjectDiff {
      public final String sourceDefinition;
      public final String targetDefinition;

      public ViewDiff(String viewName, SchemaComparator.DiffType type, String sourceDef, String targetDef) {
         super(viewName, type);
         this.sourceDefinition = sourceDef;
         this.targetDefinition = targetDef;
      }
   }
}
