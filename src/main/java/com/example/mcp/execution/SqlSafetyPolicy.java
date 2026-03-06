package com.example.mcp.execution;

import java.util.Optional;

public interface SqlSafetyPolicy {
   Optional<String> validateReadOnly(String sql);

   Optional<String> validateExecute(String sql);
}
