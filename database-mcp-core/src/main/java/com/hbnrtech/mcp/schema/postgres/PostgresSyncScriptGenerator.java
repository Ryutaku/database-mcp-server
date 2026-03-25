package com.hbnrtech.mcp.schema.postgres;

import com.hbnrtech.mcp.schema.ColumnDiff;
import com.hbnrtech.mcp.schema.ConstraintDiff;
import com.hbnrtech.mcp.schema.DiffType;
import com.hbnrtech.mcp.schema.IndexDiff;
import com.hbnrtech.mcp.schema.SchemaDiffResult;
import com.hbnrtech.mcp.schema.SequenceDiff;
import com.hbnrtech.mcp.schema.SyncScriptGenerator;
import com.hbnrtech.mcp.schema.TableDiff;
import com.hbnrtech.mcp.schema.ViewDiff;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PostgresSyncScriptGenerator implements SyncScriptGenerator {
   @Override
   public List<String> generateScripts(SchemaDiffResult result) {
      List<String> scripts = new ArrayList<>();
      String targetSchema = result.targetSchema();

      for (TableDiff diff : result.tableDiffs()) {
         switch (diff.type()) {
            case MISSING_IN_TARGET -> {
               if (diff.sourceDefinition() != null) {
                  scripts.add("-- create table: " + diff.objectName());
                  scripts.add(diff.sourceDefinition().replace(result.sourceSchema() + ".", targetSchema + ".") + ";");
               }
            }
            case MISSING_IN_SOURCE -> {
               scripts.add("-- drop extra table: " + diff.objectName());
               scripts.add("DROP TABLE " + quoteIdentifier(targetSchema) + "." + quoteIdentifier(diff.objectName()) + ";");
            }
            case DIFFERENT -> {
               String fullTableName = quoteIdentifier(targetSchema) + "." + quoteIdentifier(diff.objectName());
               for (ColumnDiff colDiff : diff.columnDiffs()) {
                  switch (colDiff.type()) {
                     case MISSING_IN_TARGET -> {
                        scripts.add("-- add column: " + diff.objectName() + "." + colDiff.columnName());
                        scripts.add("ALTER TABLE " + fullTableName + " ADD COLUMN " + quoteIdentifier(colDiff.columnName()) + " " + colDiff.sourceType() + ";");
                     }
                     case MISSING_IN_SOURCE -> {
                        scripts.add("-- drop extra column: " + diff.objectName() + "." + colDiff.columnName());
                        scripts.add("ALTER TABLE " + fullTableName + " DROP COLUMN " + quoteIdentifier(colDiff.columnName()) + ";");
                     }
                     case DIFFERENT -> {
                        if (!Objects.equals(colDiff.sourceType(), colDiff.targetType())) {
                           scripts.add("-- alter column type: " + diff.objectName() + "." + colDiff.columnName());
                           scripts.add("ALTER TABLE " + fullTableName + " ALTER COLUMN " + quoteIdentifier(colDiff.columnName()) + " TYPE " + colDiff.sourceType() + ";");
                        }
                     }
                  }
               }
            }
         }
      }

      for (IndexDiff diff : result.indexDiffs()) {
         String fullIndexName = quoteIdentifier(targetSchema) + "." + quoteIdentifier(diff.objectName());
         switch (diff.type()) {
            case MISSING_IN_TARGET -> {
               if (diff.sourceDefinition() != null) {
                  scripts.add("-- create index: " + diff.objectName());
                  scripts.add(diff.sourceDefinition().replace(result.sourceSchema() + ".", targetSchema + ".") + ";");
               }
            }
            case MISSING_IN_SOURCE -> {
               scripts.add("-- drop extra index: " + diff.objectName());
               scripts.add("DROP INDEX " + fullIndexName + ";");
            }
            case DIFFERENT -> {
               scripts.add("-- rebuild index: " + diff.objectName());
               scripts.add("DROP INDEX " + fullIndexName + ";");
               scripts.add(diff.sourceDefinition().replace(result.sourceSchema() + ".", targetSchema + ".") + ";");
            }
         }
      }

      for (ConstraintDiff diff : result.constraintDiffs()) {
         String fullTableName = quoteIdentifier(targetSchema) + "." + quoteIdentifier(diff.tableName());
         switch (diff.type()) {
            case MISSING_IN_TARGET -> {
               if (diff.sourceDefinition() != null) {
                  scripts.add("-- add constraint: " + diff.objectName());
                  scripts.add("ALTER TABLE " + fullTableName + " ADD CONSTRAINT " + quoteIdentifier(diff.objectName()) + " " + diff.sourceDefinition() + ";");
               }
            }
            case MISSING_IN_SOURCE -> {
               scripts.add("-- drop extra constraint: " + diff.objectName());
               scripts.add("ALTER TABLE " + fullTableName + " DROP CONSTRAINT " + quoteIdentifier(diff.objectName()) + ";");
            }
            case DIFFERENT -> {
               scripts.add("-- replace constraint: " + diff.objectName());
               scripts.add("ALTER TABLE " + fullTableName + " DROP CONSTRAINT " + quoteIdentifier(diff.objectName()) + ";");
               scripts.add("ALTER TABLE " + fullTableName + " ADD CONSTRAINT " + quoteIdentifier(diff.objectName()) + " " + diff.sourceDefinition() + ";");
            }
         }
      }

      for (ViewDiff diff : result.viewDiffs()) {
         String fullViewName = quoteIdentifier(targetSchema) + "." + quoteIdentifier(diff.objectName());
         switch (diff.type()) {
            case MISSING_IN_TARGET -> {
               if (diff.sourceDefinition() != null) {
                  scripts.add("-- create view: " + diff.objectName());
                  scripts.add("CREATE OR REPLACE VIEW " + fullViewName + " AS " + diff.sourceDefinition() + ";");
               }
            }
            case MISSING_IN_SOURCE -> {
               scripts.add("-- drop extra view: " + diff.objectName());
               scripts.add("DROP VIEW " + fullViewName + ";");
            }
            case DIFFERENT -> {
               scripts.add("-- replace view: " + diff.objectName());
               scripts.add("CREATE OR REPLACE VIEW " + fullViewName + " AS " + diff.sourceDefinition() + ";");
            }
         }
      }

      for (SequenceDiff diff : result.sequenceDiffs()) {
         String fullSequenceName = quoteIdentifier(targetSchema) + "." + quoteIdentifier(diff.objectName());
         switch (diff.type()) {
            case MISSING_IN_TARGET -> {
               if (diff.sourceDefinition() != null) {
                  scripts.add("-- create sequence: " + diff.objectName());
                  scripts.add(diff.sourceDefinition().replace(" " + diff.objectName() + " ", " " + fullSequenceName + " ") + ";");
               }
            }
            case MISSING_IN_SOURCE -> {
               scripts.add("-- drop extra sequence: " + diff.objectName());
               scripts.add("DROP SEQUENCE " + fullSequenceName + ";");
            }
            case DIFFERENT -> {
               scripts.add("-- review sequence difference: " + diff.objectName());
               scripts.add("-- source: " + diff.sourceDefinition());
               scripts.add("-- target: " + diff.targetDefinition());
            }
         }
      }

      return scripts;
   }

   private static String quoteIdentifier(String identifier) {
      return "\"" + identifier.replace("\"", "\"\"") + "\"";
   }
}
