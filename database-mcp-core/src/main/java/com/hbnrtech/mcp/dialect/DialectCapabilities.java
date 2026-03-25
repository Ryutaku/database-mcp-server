package com.hbnrtech.mcp.dialect;

public record DialectCapabilities(
   boolean createSchema,
   boolean switchSchema,
   boolean analyzeIndex,
   boolean getDdl,
   boolean compareSchemas
) {
}
