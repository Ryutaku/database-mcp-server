package com.hbnrtech.mcp.schema;

import java.util.List;

public interface SyncScriptGenerator {
   List<String> generateScripts(SchemaDiffResult diffResult);
}
