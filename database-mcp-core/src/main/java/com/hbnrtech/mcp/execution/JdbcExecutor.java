package com.hbnrtech.mcp.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JdbcExecutor {
   private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

   public List<Map<String, Object>> query(Connection conn, String sql) throws SQLException {
      try (Statement stmt = conn.createStatement()) {
         stmt.setQueryTimeout(60);
         try (ResultSet rs = stmt.executeQuery(sql)) {
            return this.resultSetToList(rs);
         }
      }
   }

   public List<Map<String, Object>> query(Connection conn, String sql, List<Object> params) throws SQLException {
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
         stmt.setQueryTimeout(60);

         for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
         }

         try (ResultSet rs = stmt.executeQuery()) {
            return this.resultSetToList(rs);
         }
      }
   }

   public StatementResult execute(Connection conn, String sql) throws SQLException {
      try (Statement stmt = conn.createStatement()) {
         stmt.setQueryTimeout(60);
         boolean hasResultSet = stmt.execute(sql);
         if (hasResultSet) {
            try (ResultSet rs = stmt.getResultSet()) {
               return new StatementResult(true, this.resultSetToList(rs), -1);
            }
         }

         return new StatementResult(false, List.of(), stmt.getUpdateCount());
      }
   }

   public String toJson(List<Map<String, Object>> rows) throws JsonProcessingException {
      return this.objectMapper.writeValueAsString(rows);
   }

   public boolean isTimeoutException(SQLException e) {
      String message = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
      return message.contains("timeout") || message.contains("canceling statement") || message.contains("query cancelled") || message.contains("ora-01013");
   }

   private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
      List<Map<String, Object>> list = new ArrayList<>();
      ResultSetMetaData meta = rs.getMetaData();
      int columnCount = meta.getColumnCount();

      while (rs.next()) {
         Map<String, Object> row = new LinkedHashMap<>();

         for (int i = 1; i <= columnCount; i++) {
            row.put(meta.getColumnLabel(i), rs.getObject(i));
         }

         list.add(row);
      }

      return list;
   }

   public record StatementResult(boolean hasResultSet, List<Map<String, Object>> rows, int updateCount) {
   }
}
