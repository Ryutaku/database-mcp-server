package com.example.mcp.schema;

import java.util.Map;

public record TableDef(String name, String schema, String ddl, Map<String, ColumnDef> columns) {
}
