package com.hbnrtech.mcp.bootstrap;

import com.hbnrtech.mcp.execution.DatasourceRegistry;
import com.hbnrtech.mcp.execution.JdbcExecutor;
import com.hbnrtech.mcp.tools.GenericMcpTools;

public record DatabaseMcpRuntime(
   DatasourceRegistry datasourceRegistry,
   JdbcExecutor executor,
   GenericMcpTools tools
) {
}
