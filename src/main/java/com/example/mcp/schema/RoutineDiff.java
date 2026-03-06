package com.example.mcp.schema;

public record RoutineDiff(String objectName, DiffType type, String sourceDefinition, String targetDefinition) {
}
