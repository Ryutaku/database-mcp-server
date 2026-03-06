package com.example.mcp.schema;

import java.util.List;

public interface SyncScriptGenerator {
   List<String> generateScripts(SchemaDiffResult diffResult);
}
