package com.hbnrtech.mcp.schema;

public record ViewDiff(String objectName, DiffType type, String sourceDefinition, String targetDefinition) {
}
