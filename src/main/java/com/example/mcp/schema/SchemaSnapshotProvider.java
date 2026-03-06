package com.example.mcp.schema;

import java.sql.Connection;
import java.sql.SQLException;

public interface SchemaSnapshotProvider {
   SchemaSnapshot loadSnapshot(Connection connection, String schema) throws SQLException;

   String buildTableDdl(Connection connection, String schema, String tableName) throws SQLException;
}
