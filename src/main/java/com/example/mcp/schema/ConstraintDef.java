package com.example.mcp.schema;

public record ConstraintDef(String name, String tableName, String constraintType, String definition) {
}
