package com.example.mcp.schema;

public record ViewDiff(String objectName, DiffType type, String sourceDefinition, String targetDefinition) {
}
