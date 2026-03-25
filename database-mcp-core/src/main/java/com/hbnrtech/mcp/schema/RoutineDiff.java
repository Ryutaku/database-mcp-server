package com.hbnrtech.mcp.schema;

public record RoutineDiff(String objectName, DiffType type, String sourceDefinition, String targetDefinition) {
}
