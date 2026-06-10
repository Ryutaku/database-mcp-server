package com.hbnrtech.mcp.schema.mysql;

import com.hbnrtech.mcp.schema.SchemaDiffResult;
import com.hbnrtech.mcp.schema.SyncScriptGenerator;
import java.util.List;

public class MySqlSyncScriptGenerator implements SyncScriptGenerator {
   @Override
   public List<String> generateScripts(SchemaDiffResult diffResult) {
      return List.of();
   }
}
