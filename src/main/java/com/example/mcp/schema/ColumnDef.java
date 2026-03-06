package com.example.mcp.schema;

public record ColumnDef(String name, String dataType, boolean nullable, String defaultValue, int ordinalPosition) {
}
