package com.hbnrtech.mcp.schema;

import java.util.Map;

public record SchemaSnapshot(
   String schemaName,
   Map<String, TableDef> tables,
   Map<String, IndexDef> indexes,
   Map<String, ConstraintDef> constraints,
   Map<String, ViewDef> views,
   Map<String, RoutineDef> routines,
   Map<String, SequenceDef> sequences
) {
}
