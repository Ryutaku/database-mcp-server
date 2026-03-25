package com.hbnrtech.mcp.schema;

public record ColumnDiff(String columnName, DiffType type, String sourceType, String targetType, boolean nullableDiff, boolean defaultDiff) {
}
