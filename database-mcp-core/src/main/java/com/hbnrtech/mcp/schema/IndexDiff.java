package com.hbnrtech.mcp.schema;

public record IndexDiff(String objectName, DiffType type, String tableName, String sourceDefinition, String targetDefinition) {
}
