package com.hbnrtech.mcp.execution;

import com.hbnrtech.mcp.config.DatasourceConfig;
import com.hbnrtech.mcp.dialect.DatabaseDialect;

public record DatasourceContext(
   DatasourceConfig config,
   DatabaseDialect dialect,
   ConnectionManager connectionManager
) {
}
