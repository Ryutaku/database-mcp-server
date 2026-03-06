package com.example.mcp.schema;

public record SequenceDiff(String objectName, DiffType type, String sourceDefinition, String targetDefinition) {
}
