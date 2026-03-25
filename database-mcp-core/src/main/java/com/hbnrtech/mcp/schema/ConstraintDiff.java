package com.hbnrtech.mcp.schema;

public record ConstraintDiff(String objectName, DiffType type, String tableName, String constraintType, String sourceDefinition, String targetDefinition) {
}
