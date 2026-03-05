package com.example.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.modelcontextprotocol.spec.McpSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class PgsqlTools {
   private final HikariDataSource dataSource;
   private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

   public PgsqlTools(String dbUrl, String dbUser, String dbPassword) {
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl(dbUrl);
      config.setUsername(dbUser);
      config.setPassword(dbPassword);
      config.setMaximumPoolSize(5);
      config.setMinimumIdle(2);
      config.setIdleTimeout(300000L);
      config.setConnectionTimeout(30000L);
      config.setMaxLifetime(1800000L);
      config.setKeepaliveTime(60000L);
      config.setPoolName("PostgresMCPPool");
      config.setConnectionTestQuery("SELECT 1");
      config.setValidationTimeout(5000L);
      config.addDataSourceProperty("socketTimeout", "60");
      config.addDataSourceProperty("connectTimeout", "30");
      config.addDataSourceProperty("cachePrepStmts", "true");
      config.addDataSourceProperty("prepStmtCacheSize", "250");
      config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
      this.dataSource = new HikariDataSource(config);
   }

   public void close() {
      if (this.dataSource != null && !this.dataSource.isClosed()) {
         this.dataSource.close();
      }
   }

   public McpSchema.Tool getQueryTool() {
      return new McpSchema.Tool(
         "pg_query",
         "执行 SELECT 查询语句",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"sql\": {\"type\": \"string\", \"description\": \"SELECT SQL语句\"}\n    },\n    \"required\": [\"sql\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getQueryHandler() {
      return args -> this.executeSelect((String)args.get("sql"));
   }

   public McpSchema.Tool getSwitchSchemaTool() {
      return new McpSchema.Tool(
         "pg_switch_schema",
         "切换当前 Schema，后续操作将在指定 Schema 下执行",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"}\n    },\n    \"required\": [\"schema\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getSwitchSchemaHandler() {
      return args -> {
         String schema = (String)args.get("schema");
         String sql = "SET search_path TO " + this.quoteIdentifier(schema);
         return this.executeDDL(sql, "已切换到 schema: " + schema);
      };
   }

   public McpSchema.Tool getListSchemasTool() {
      return new McpSchema.Tool("pg_list_schemas", "列出所有 Schema", "{\n    \"type\": \"object\",\n    \"properties\": {}\n}\n");
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getListSchemasHandler() {
      return args -> {
         String sql = "SELECT schema_name, schema_owner,\n       (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = s.schema_name) as table_count\nFROM information_schema.schemata s\nWHERE schema_name NOT LIKE 'pg_%' AND schema_name != 'information_schema'\nORDER BY schema_name\n";
         return this.executeSelect(sql);
      };
   }

   public McpSchema.Tool getCreateSchemaTool() {
      return new McpSchema.Tool(
         "pg_create_schema",
         "创建新的 Schema",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\"},\n        \"ifNotExists\": {\"type\": \"boolean\", \"description\": \"如果不存在则创建\", \"default\": true}\n    },\n    \"required\": [\"schema\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getCreateSchemaHandler() {
      return args -> {
         String schema = (String)args.get("schema");
         boolean ifNotExists = (Boolean)args.getOrDefault("ifNotExists", true);
         String sql = "CREATE SCHEMA " + (ifNotExists ? "IF NOT EXISTS " : "") + this.quoteIdentifier(schema);
         return this.executeDDL(sql, "Schema '" + schema + "' 创建成功");
      };
   }

   public McpSchema.Tool getListTablesTool() {
      return new McpSchema.Tool(
         "pg_list_tables",
         "列出指定 Schema 中的所有表",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"}\n    }\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getListTablesHandler() {
      return args -> {
         String schema = (String)args.getOrDefault("schema", "public");
         String sql = "SELECT t.table_name, pgd.description as table_comment,\n       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = t.table_schema AND table_name = t.table_name) as column_count\nFROM information_schema.tables t\nLEFT JOIN pg_catalog.pg_description pgd ON pgd.objoid = (quote_ident(t.table_schema) || '.' || quote_ident(t.table_name))::regclass::oid AND pgd.objsubid = 0\nWHERE t.table_schema = ? AND t.table_type = 'BASE TABLE'\nORDER BY t.table_name\n";
         return this.executeSelectWithParam(sql, schema);
      };
   }

   public McpSchema.Tool getDescribeTableTool() {
      return new McpSchema.Tool(
         "pg_describe_table",
         "获取表结构详情",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"tableName\": {\"type\": \"string\", \"description\": \"表名\"},\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"}\n    },\n    \"required\": [\"tableName\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getDescribeTableHandler() {
      return args -> {
         String tableName = (String)args.get("tableName");
         String schema = (String)args.getOrDefault("schema", "public");
         String sql = "SELECT c.column_name, c.data_type, c.character_maximum_length, c.numeric_precision, c.numeric_scale,\n       c.is_nullable, c.column_default, pgd.description as column_comment\nFROM information_schema.columns c\nLEFT JOIN pg_catalog.pg_statio_all_tables st ON c.table_schema = st.schemaname AND c.table_name = st.relname\nLEFT JOIN pg_catalog.pg_description pgd ON pgd.objoid = st.relid AND pgd.objsubid = c.ordinal_position\nWHERE c.table_schema = ? AND c.table_name = ?\nORDER BY c.ordinal_position\n";
         return this.executeSelectWithParams(sql, schema, tableName);
      };
   }

   public McpSchema.Tool getCreateTableTool() {
      return new McpSchema.Tool(
         "pg_create_table",
         "创建新表，支持定义列、主键、外键等",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"tableName\": {\"type\": \"string\", \"description\": \"表名\"},\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"},\n        \"columns\": {\"type\": \"array\", \"description\": \"列定义数组\", \"items\": {\"type\": \"object\"}},\n        \"ifNotExists\": {\"type\": \"boolean\", \"description\": \"如果不存在则创建\", \"default\": true}\n    },\n    \"required\": [\"tableName\", \"columns\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getCreateTableHandler() {
      return args -> {
         String tableName = (String)args.get("tableName");
         String schema = (String)args.getOrDefault("schema", "public");
         List<Map<String, Object>> columns = (List<Map<String, Object>>)args.get("columns");
         boolean ifNotExists = (Boolean)args.getOrDefault("ifNotExists", true);
         StringBuilder sql = new StringBuilder("CREATE TABLE ");
         if (ifNotExists) {
            sql.append("IF NOT EXISTS ");
         }

         sql.append(this.quoteIdentifier(schema)).append(".").append(this.quoteIdentifier(tableName)).append(" (");
         List<String> columnDefs = new ArrayList<>();

         for (Map<String, Object> col : columns) {
            StringBuilder colDef = new StringBuilder();
            colDef.append(this.quoteIdentifier((String)col.get("name")));
            colDef.append(" ").append(col.get("type"));
            if (Boolean.TRUE.equals(col.get("notNull"))) {
               colDef.append(" NOT NULL");
            }

            if (Boolean.TRUE.equals(col.get("primaryKey"))) {
               colDef.append(" PRIMARY KEY");
            }

            if (col.get("default") != null) {
               colDef.append(" DEFAULT ").append(col.get("default"));
            }

            columnDefs.add(colDef.toString());
         }

         sql.append(String.join(", ", columnDefs));
         sql.append(")");
         return this.executeDDL(sql.toString(), "表 '" + schema + "." + tableName + "' 创建成功");
      };
   }

   public McpSchema.Tool getAlterTableTool() {
      return new McpSchema.Tool(
         "pg_alter_table",
         "修改表结构：添加列、修改列、删除列、重命名列",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"tableName\": {\"type\": \"string\", \"description\": \"表名\"},\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"},\n        \"action\": {\"type\": \"string\", \"description\": \"操作类型\", \"enum\": [\"add_column\", \"drop_column\", \"rename_column\", \"alter_column\", \"add_constraint\", \"drop_constraint\"]},\n        \"columnDef\": {\"type\": \"object\", \"description\": \"列定义（add_column/alter_column 时使用）\"},\n        \"columnName\": {\"type\": \"string\", \"description\": \"列名（drop_column/rename_column/alter_column 时使用）\"},\n        \"newColumnName\": {\"type\": \"string\", \"description\": \"新列名（rename_column 时使用）\"},\n        \"constraintName\": {\"type\": \"string\", \"description\": \"约束名（add_constraint/drop_constraint 时使用）\"},\n        \"constraintDef\": {\"type\": \"string\", \"description\": \"约束定义（add_constraint 时使用）\"}\n    },\n    \"required\": [\"tableName\", \"action\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getAlterTableHandler() {
      return args -> {
         String tableName = (String)args.get("tableName");
         String schema = (String)args.getOrDefault("schema", "public");
         String action = (String)args.get("action");
         String fullTableName = this.quoteIdentifier(schema) + "." + this.quoteIdentifier(tableName);
         StringBuilder sql = new StringBuilder("ALTER TABLE ").append(fullTableName).append(" ");
         switch (action) {
            case "add_column": {
               Map<String, Object> colDef = (Map<String, Object>)args.get("columnDef");
               sql.append("ADD COLUMN ").append(this.quoteIdentifier((String)colDef.get("name")));
               sql.append(" ").append(colDef.get("type"));
               if (Boolean.TRUE.equals(colDef.get("notNull"))) {
                  sql.append(" NOT NULL");
               }

               if (colDef.get("default") != null) {
                  sql.append(" DEFAULT ").append(colDef.get("default"));
               }
               break;
            }
            case "drop_column":
               sql.append("DROP COLUMN ").append(this.quoteIdentifier((String)args.get("columnName")));
               break;
            case "rename_column":
               sql.append("RENAME COLUMN ")
                  .append(this.quoteIdentifier((String)args.get("columnName")))
                  .append(" TO ")
                  .append(this.quoteIdentifier((String)args.get("newColumnName")));
               break;
            case "alter_column": {
               Map<String, Object> colDef = (Map<String, Object>)args.get("columnDef");
               String colName = this.quoteIdentifier((String)colDef.get("name"));
               sql.append("ALTER COLUMN ").append(colName).append(" TYPE ").append(colDef.get("type"));
               break;
            }
            case "add_constraint":
               sql.append("ADD CONSTRAINT ").append(this.quoteIdentifier((String)args.get("constraintName"))).append(" ").append(args.get("constraintDef"));
               break;
            case "drop_constraint":
               sql.append("DROP CONSTRAINT ").append(this.quoteIdentifier((String)args.get("constraintName")));
               break;
            default:
               throw new IllegalArgumentException("不支持的操作: " + action);
         }

         return this.executeDDL(sql.toString(), "表 '" + schema + "." + tableName + "' 修改成功");
      };
   }

   public McpSchema.Tool getDropTableTool() {
      return new McpSchema.Tool(
         "pg_drop_table",
         "删除表",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"tableName\": {\"type\": \"string\", \"description\": \"表名\"},\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"},\n        \"ifExists\": {\"type\": \"boolean\", \"description\": \"如果存在则删除\", \"default\": true},\n        \"cascade\": {\"type\": \"boolean\", \"description\": \"级联删除依赖\", \"default\": false}\n    },\n    \"required\": [\"tableName\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getDropTableHandler() {
      return args -> {
         String tableName = (String)args.get("tableName");
         String schema = (String)args.getOrDefault("schema", "public");
         boolean ifExists = (Boolean)args.getOrDefault("ifExists", true);
         boolean cascade = (Boolean)args.getOrDefault("cascade", false);
         String sql = "DROP TABLE "
            + (ifExists ? "IF EXISTS " : "")
            + this.quoteIdentifier(schema)
            + "."
            + this.quoteIdentifier(tableName)
            + (cascade ? " CASCADE" : "");
         return this.executeDDL(sql, "表 '" + schema + "." + tableName + "' 已删除");
      };
   }

   public McpSchema.Tool getGetDDLTool() {
      return new McpSchema.Tool(
         "pg_get_ddl",
         "获取表的 DDL 语句",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"tableName\": {\"type\": \"string\", \"description\": \"表名\"},\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"}\n    },\n    \"required\": [\"tableName\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getGetDDLHandler() {
      return args -> {
         String tableName = (String)args.get("tableName");
         String schema = (String)args.getOrDefault("schema", "public");

         try {
            McpSchema.CallToolResult var7;
            try (Connection conn = this.getConnection()) {
               SchemaComparator comparator = new SchemaComparator(conn);
               String ddl = comparator.buildTableDefinition(schema, tableName);
               var7 = this.successResult(ddl);
            }

            return var7;
         } catch (SQLException var10) {
            return this.errorResult("获取 DDL 失败: " + var10.getMessage());
         }
      };
   }

   public McpSchema.Tool getListIndexesTool() {
      return new McpSchema.Tool(
         "pg_list_indexes",
         "列出表的索引",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"tableName\": {\"type\": \"string\", \"description\": \"表名（可选，不提供则列出所有索引）\"},\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"}\n    }\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getListIndexesHandler() {
      return args -> {
         String schema = (String)args.getOrDefault("schema", "public");
         String tableName = (String)args.get("tableName");
         if (tableName != null && !tableName.isEmpty()) {
            String sql = "SELECT indexname as index_name, indexdef as index_definition,\n       pg_size_pretty(pg_relation_size(indexrelid)) as index_size\nFROM pg_indexes\nJOIN pg_class ON pg_class.relname = indexname\nWHERE schemaname = ? AND tablename = ?\nORDER BY indexname\n";
            return this.executeSelectWithParams(sql, schema, tableName);
         } else {
            String sql = "SELECT schemaname, tablename, indexname as index_name, indexdef as index_definition\nFROM pg_indexes\nWHERE schemaname = ?\nORDER BY tablename, indexname\n";
            return this.executeSelectWithParam(sql, schema);
         }
      };
   }

   public McpSchema.Tool getCreateIndexTool() {
      return new McpSchema.Tool(
         "pg_create_index",
         "创建索引",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"indexName\": {\"type\": \"string\", \"description\": \"索引名\"},\n        \"tableName\": {\"type\": \"string\", \"description\": \"表名\"},\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"},\n        \"columns\": {\"type\": \"array\", \"description\": \"列名数组\", \"items\": {\"type\": \"string\"}},\n        \"unique\": {\"type\": \"boolean\", \"description\": \"是否唯一索引\", \"default\": false},\n        \"ifNotExists\": {\"type\": \"boolean\", \"description\": \"如果不存在则创建\", \"default\": true}\n    },\n    \"required\": [\"indexName\", \"tableName\", \"columns\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getCreateIndexHandler() {
      return args -> {
         String indexName = (String)args.get("indexName");
         String tableName = (String)args.get("tableName");
         String schema = (String)args.getOrDefault("schema", "public");
         List<String> columns = (List<String>)args.get("columns");
         boolean unique = (Boolean)args.getOrDefault("unique", false);
         boolean ifNotExists = (Boolean)args.getOrDefault("ifNotExists", true);
         String cols = String.join(", ", columns.stream().map(this::quoteIdentifier).toList());
         String sql = (unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
            + (ifNotExists ? "IF NOT EXISTS " : "")
            + this.quoteIdentifier(indexName)
            + " ON "
            + this.quoteIdentifier(schema)
            + "."
            + this.quoteIdentifier(tableName)
            + " ("
            + cols
            + ")";
         return this.executeDDL(sql, "索引 '" + indexName + "' 创建成功");
      };
   }

   public McpSchema.Tool getDropIndexTool() {
      return new McpSchema.Tool(
         "pg_drop_index",
         "删除索引",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"indexName\": {\"type\": \"string\", \"description\": \"索引名\"},\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"},\n        \"ifExists\": {\"type\": \"boolean\", \"description\": \"如果存在则删除\", \"default\": true}\n    },\n    \"required\": [\"indexName\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getDropIndexHandler() {
      return args -> {
         String indexName = (String)args.get("indexName");
         String schema = (String)args.getOrDefault("schema", "public");
         boolean ifExists = (Boolean)args.getOrDefault("ifExists", true);
         String sql = "DROP INDEX " + (ifExists ? "IF EXISTS " : "") + this.quoteIdentifier(schema) + "." + this.quoteIdentifier(indexName);
         return this.executeDDL(sql, "索引 '" + indexName + "' 已删除");
      };
   }

   public McpSchema.Tool getAnalyzeIndexTool() {
      return new McpSchema.Tool(
         "pg_analyze_index",
         "分析索引使用情况",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"tableName\": {\"type\": \"string\", \"description\": \"表名（可选）\"},\n        \"schema\": {\"type\": \"string\", \"description\": \"Schema 名称\", \"default\": \"public\"}\n    }\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getAnalyzeIndexHandler() {
      return args -> {
         String schema = (String)args.getOrDefault("schema", "public");
         String tableName = (String)args.get("tableName");
         String sql;
         if (tableName != null) {
            sql = "SELECT schemaname, relname as table_name, indexrelname as index_name,\n       idx_scan as index_scans, idx_tup_read as tuples_read, idx_tup_fetch as tuples_fetched\nFROM pg_stat_user_indexes\nWHERE schemaname = ? AND relname = ?\nORDER BY idx_scan DESC\n";
         } else {
            sql = "SELECT schemaname, relname as table_name, indexrelname as index_name,\n       idx_scan as index_scans, idx_tup_read as tuples_read, idx_tup_fetch as tuples_fetched\nFROM pg_stat_user_indexes\nWHERE schemaname = ?\nORDER BY idx_scan DESC\n";
         }

         return tableName != null ? this.executeSelectWithParams(sql, schema, tableName) : this.executeSelectWithParam(sql, schema);
      };
   }

   public McpSchema.Tool getExecuteTool() {
      return new McpSchema.Tool(
         "pg_execute",
         "执行 INSERT/UPDATE/DELETE/CREATE/ALTER/DROP 等 DDL/DML 语句",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"sql\": {\"type\": \"string\", \"description\": \"SQL 执行语句\"}\n    },\n    \"required\": [\"sql\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getExecuteHandler() {
      return args -> {
         String sql = (String)args.get("sql");
         McpSchema.CallToolResult securityCheck = this.checkDangerousOperations(sql);
         if (securityCheck != null) {
            return securityCheck;
         } else {
            try {
               McpSchema.CallToolResult var20;
               try (
                  Connection conn = this.getConnection();
                  Statement stmt = conn.createStatement();
               ) {
                  stmt.setQueryTimeout(60);
                  boolean hasResultSet = stmt.execute(sql);
                  if (hasResultSet) {
                     try (ResultSet rs = stmt.getResultSet()) {
                        List<Map<String, Object>> results = this.resultSetToList(rs);
                        String json = this.objectMapper.writeValueAsString(results);
                        return this.successResult("执行成功，返回数据:\n" + json);
                     }
                  }

                  int updateCount = stmt.getUpdateCount();
                  var20 = this.successResult("执行成功，影响行数: " + updateCount);
               }

               return var20;
            } catch (SQLException var17) {
               return !var17.getMessage().toLowerCase().contains("timeout")
                     && !var17.getMessage().toLowerCase().contains("canceling statement")
                     && !var17.getMessage().toLowerCase().contains("query cancelled")
                  ? this.errorResult("SQL 错误: " + var17.getMessage())
                  : this.errorResult("SQL 执行超时（60秒）: 数据库可能繁忙或存在锁冲突。建议：1) 检查是否有长时间运行的事务 2) 查看 pg_stat_activity 3) 稍后重试");
            } catch (JsonProcessingException var18) {
               return this.errorResult("JSON 序列化错误: " + var18.getMessage());
            }
         }
      };
   }

   public McpSchema.Tool getDbInfoTool() {
      return new McpSchema.Tool("pg_db_info", "获取数据库信息（版本、大小、连接数等）", "{\n    \"type\": \"object\",\n    \"properties\": {}\n}\n");
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getDbInfoHandler() {
      return args -> {
         String sql = "SELECT\n    current_database() as database_name,\n    version() as version,\n    pg_size_pretty(pg_database_size(current_database())) as database_size,\n    (SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database()) as active_connections\n";
         return this.executeSelect(sql);
      };
   }

   public McpSchema.Tool getCurrentUserTool() {
      return new McpSchema.Tool("pg_current_user", "获取当前连接的用户和权限信息", "{\n    \"type\": \"object\",\n    \"properties\": {}\n}\n");
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getCurrentUserHandler() {
      return args -> {
         String sql = "SELECT\n    current_user as username,\n    session_user as session_user,\n    current_database() as database,\n    current_schema() as current_schema,\n    (SELECT pg_has_role(current_user, 'rds_superuser', 'MEMBER')) as is_superuser\n";
         return this.executeSelect(sql);
      };
   }

   public McpSchema.Tool getCompareSchemaTool() {
      return new McpSchema.Tool(
         "pg_compare_schemas",
         "比较两个 Schema 的结构差异，生成同步 DDL 脚本",
         "{\n    \"type\": \"object\",\n    \"properties\": {\n        \"sourceSchema\": {\"type\": \"string\", \"description\": \"源 Schema 名称（作为参照）\"},\n        \"targetSchema\": {\"type\": \"string\", \"description\": \"目标 Schema 名称（需要同步的）\"}\n    },\n    \"required\": [\"sourceSchema\", \"targetSchema\"]\n}\n"
      );
   }

   public Function<Map<String, Object>, McpSchema.CallToolResult> getCompareSchemaHandler() {
      return args -> {
         String sourceSchema = (String)args.get("sourceSchema");
         String targetSchema = (String)args.get("targetSchema");
         if (sourceSchema == null || sourceSchema.trim().isEmpty()) {
            return this.errorResult("源 Schema 名称不能为空");
         } else if (targetSchema == null || targetSchema.trim().isEmpty()) {
            return this.errorResult("目标 Schema 名称不能为空");
         } else if (sourceSchema.equals(targetSchema)) {
            return this.errorResult("源 Schema 和目标 Schema 不能相同");
         } else {
            try {
               McpSchema.CallToolResult var22;
               try (Connection conn = this.getConnection()) {
                  SchemaComparator comparator = new SchemaComparator(conn);
                  SchemaComparator.SchemaDiffResult result = comparator.compareSchemas(sourceSchema, targetSchema);
                  StringBuilder report = new StringBuilder();
                  report.append("=== Schema 比较结果 ===\\n");
                  report.append("源 Schema: ").append(sourceSchema).append("\\n");
                  report.append("目标 Schema: ").append(targetSchema).append("\\n\\n");
                  int totalDiffs = result.tableDiffs.size()
                     + result.indexDiffs.size()
                     + result.constraintDiffs.size()
                     + result.viewDiffs.size()
                     + result.functionDiffs.size()
                     + result.sequenceDiffs.size();
                  if (!result.hasDifferences()) {
                     report.append("✓ 两个 Schema 结构完全一致，没有发现差异\\n");
                     return this.successResult(report.toString());
                  }

                  report.append("发现 ").append(totalDiffs).append(" 处差异：\\n");
                  report.append("  - 表差异: ").append(result.tableDiffs.size()).append("\\n");
                  report.append("  - 索引差异: ").append(result.indexDiffs.size()).append("\\n");
                  report.append("  - 约束差异: ").append(result.constraintDiffs.size()).append("\\n");
                  report.append("  - 视图差异: ").append(result.viewDiffs.size()).append("\\n");
                  report.append("  - 函数差异: ").append(result.functionDiffs.size()).append("\\n");
                  report.append("  - 序列差异: ").append(result.sequenceDiffs.size()).append("\\n\\n");
                  report.append("=== 详细差异 ===\\n\\n");
                  if (!result.tableDiffs.isEmpty()) {
                     report.append("【表差异】\\n");

                     for (SchemaComparator.TableDiff diff : result.tableDiffs) {
                        report.append("  表: ").append(diff.objectName).append(" - ");
                        switch (diff.type) {
                           case MISSING_IN_TARGET:
                              report.append("目标 Schema 缺少此表\\n");
                              if (!diff.columnDiffs.isEmpty()) {
                                 report.append("    列数: ").append(diff.columnDiffs.size()).append("\\n");
                              }
                              break;
                           case MISSING_IN_SOURCE:
                              report.append("源 Schema 缺少此表（目标有多余表）\\n");
                              break;
                           case DIFFERENT:
                              report.append("表结构不同\\n");

                              for (SchemaComparator.ColumnDiff colDiff : diff.columnDiffs) {
                                 report.append("    列 ").append(colDiff.columnName).append(": ");
                                 switch (colDiff.type) {
                                    case MISSING_IN_TARGET:
                                       report.append("目标缺少 (类型: ").append(colDiff.sourceType).append(")\\n");
                                       break;
                                    case MISSING_IN_SOURCE:
                                       report.append("目标有多余列\\n");
                                       break;
                                    case DIFFERENT:
                                       report.append("类型不同 (源: ").append(colDiff.sourceType).append(", 目标: ").append(colDiff.targetType).append(")\\n");
                                 }
                              }
                        }
                     }

                     report.append("\\n");
                  }

                  if (!result.indexDiffs.isEmpty()) {
                     report.append("【索引差异】\\n");

                     for (SchemaComparator.IndexDiff diff : result.indexDiffs) {
                        report.append("  索引: ").append(diff.objectName).append(" (表: ").append(diff.tableName).append(") - ");
                        switch (diff.type) {
                           case MISSING_IN_TARGET:
                              report.append("目标缺少\\n");
                              break;
                           case MISSING_IN_SOURCE:
                              report.append("目标有多余\\n");
                              break;
                           case DIFFERENT:
                              report.append("定义不同\\n");
                        }
                     }

                     report.append("\\n");
                  }

                  if (!result.constraintDiffs.isEmpty()) {
                     report.append("【约束差异】\\n");

                     for (SchemaComparator.ConstraintDiff diff : result.constraintDiffs) {
                        report.append("  约束: ")
                           .append(diff.objectName)
                           .append(" (表: ")
                           .append(diff.tableName)
                           .append(", 类型: ")
                           .append(diff.constraintType)
                           .append(") - ");
                        switch (diff.type) {
                           case MISSING_IN_TARGET:
                              report.append("目标缺少\\n");
                              break;
                           case MISSING_IN_SOURCE:
                              report.append("目标有多余\\n");
                              break;
                           case DIFFERENT:
                              report.append("定义不同\\n");
                        }
                     }

                     report.append("\\n");
                  }

                  if (!result.viewDiffs.isEmpty()) {
                     report.append("【视图差异】\\n");

                     for (SchemaComparator.ViewDiff diff : result.viewDiffs) {
                        report.append("  视图: ").append(diff.objectName).append(" - ");
                        switch (diff.type) {
                           case MISSING_IN_TARGET:
                              report.append("目标缺少\\n");
                              break;
                           case MISSING_IN_SOURCE:
                              report.append("目标有多余\\n");
                              break;
                           case DIFFERENT:
                              report.append("定义不同\\n");
                        }
                     }

                     report.append("\\n");
                  }

                  if (!result.functionDiffs.isEmpty()) {
                     report.append("【函数差异】\\n");

                     for (SchemaComparator.FunctionDiff diff : result.functionDiffs) {
                        report.append("  函数: ").append(diff.objectName).append(" - ");
                        switch (diff.type) {
                           case MISSING_IN_TARGET:
                              report.append("目标缺少\\n");
                              break;
                           case MISSING_IN_SOURCE:
                              report.append("目标有多余\\n");
                              break;
                           case DIFFERENT:
                              report.append("定义不同\\n");
                        }
                     }

                     report.append("\\n");
                  }

                  if (!result.sequenceDiffs.isEmpty()) {
                     report.append("【序列差异】\\n");

                     for (SchemaComparator.SequenceDiff diff : result.sequenceDiffs) {
                        report.append("  序列: ").append(diff.objectName).append(" - ");
                        switch (diff.type) {
                           case MISSING_IN_TARGET:
                              report.append("目标缺少\\n");
                              break;
                           case MISSING_IN_SOURCE:
                              report.append("目标有多余\\n");
                              break;
                           case DIFFERENT:
                              report.append("定义不同\\n");
                        }
                     }

                     report.append("\\n");
                  }

                  report.append("=== 同步 DDL 脚本（使目标 Schema 与源 Schema 一致）===\\n\\n");
                  if (result.syncScripts.isEmpty()) {
                     report.append("-- 无需同步\\n");
                  } else {
                     report.append("-- 执行以下 SQL 语句将目标 Schema 同步为与源 Schema 一致\\n");
                     report.append("-- 注意：\\n");
                     report.append("-- 1. 建议在执行前备份目标 Schema\\n");
                     report.append("-- 2. 带有 \"-- 注意:\" 的语句需要人工确认\\n");
                     report.append("-- 3. 生产环境请先在测试环境验证\\n\\n");
                     report.append("BEGIN;\\n\\n");

                     for (String script : result.syncScripts) {
                        report.append(script).append("\\n");
                     }

                     report.append("\\nCOMMIT;\\n");
                  }

                  var22 = this.successResult(report.toString());
               }

               return var22;
            } catch (SQLException var15) {
               return this.errorResult("Schema 比较失败: " + var15.getMessage());
            }
         }
      };
   }

   private McpSchema.CallToolResult executeSelect(String sql) {
      try {
         McpSchema.CallToolResult var7;
         try (
            Connection conn = this.getConnection();
            Statement stmt = conn.createStatement();
         ) {
            stmt.setQueryTimeout(60);

            try (ResultSet rs = stmt.executeQuery(sql)) {
               List<Map<String, Object>> results = this.resultSetToList(rs);
               String json = this.objectMapper.writeValueAsString(results);
               var7 = this.successResult(json);
            }
         }

         return var7;
      } catch (SQLException var14) {
         return !var14.getMessage().toLowerCase().contains("timeout")
               && !var14.getMessage().toLowerCase().contains("canceling statement")
               && !var14.getMessage().toLowerCase().contains("query cancelled")
            ? this.errorResult("SQL 错误: " + var14.getMessage())
            : this.errorResult("查询超时（60秒）: 数据库可能繁忙或查询过于复杂。建议：1) 添加 LIMIT 限制返回行数 2) 检查是否有长时间运行的事务 3) 优化查询条件");
      } catch (JsonProcessingException var15) {
         return this.errorResult("JSON 序列化错误: " + var15.getMessage());
      }
   }

   private McpSchema.CallToolResult executeSelectWithParam(String sql, String param) {
      try {
         McpSchema.CallToolResult var8;
         try (
            Connection conn = this.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
         ) {
            stmt.setQueryTimeout(60);
            stmt.setString(1, param);

            try (ResultSet rs = stmt.executeQuery()) {
               List<Map<String, Object>> results = this.resultSetToList(rs);
               String json = this.objectMapper.writeValueAsString(results);
               var8 = this.successResult(json);
            }
         }

         return var8;
      } catch (SQLException var15) {
         return !var15.getMessage().toLowerCase().contains("timeout")
               && !var15.getMessage().toLowerCase().contains("canceling statement")
               && !var15.getMessage().toLowerCase().contains("query cancelled")
            ? this.errorResult("SQL 错误: " + var15.getMessage())
            : this.errorResult("查询超时（60秒）: 数据库可能繁忙或查询过于复杂。建议：1) 添加 LIMIT 限制返回行数 2) 检查是否有长时间运行的事务 3) 优化查询条件");
      } catch (JsonProcessingException var16) {
         return this.errorResult("JSON 序列化错误: " + var16.getMessage());
      }
   }

   private McpSchema.CallToolResult executeSelectWithParams(String sql, String... params) {
      try {
         McpSchema.CallToolResult var8;
         try (
            Connection conn = this.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
         ) {
            stmt.setQueryTimeout(60);

            for (int i = 0; i < params.length; i++) {
               stmt.setString(i + 1, params[i]);
            }

            try (ResultSet rs = stmt.executeQuery()) {
               List<Map<String, Object>> results = this.resultSetToList(rs);
               String json = this.objectMapper.writeValueAsString(results);
               var8 = this.successResult(json);
            }
         }

         return var8;
      } catch (SQLException var15) {
         return !var15.getMessage().toLowerCase().contains("timeout")
               && !var15.getMessage().toLowerCase().contains("canceling statement")
               && !var15.getMessage().toLowerCase().contains("query cancelled")
            ? this.errorResult("SQL 错误: " + var15.getMessage())
            : this.errorResult("查询超时（60秒）: 数据库可能繁忙或查询过于复杂。建议：1) 添加 LIMIT 限制返回行数 2) 检查是否有长时间运行的事务 3) 优化查询条件");
      } catch (JsonProcessingException var16) {
         return this.errorResult("JSON 序列化错误: " + var16.getMessage());
      }
   }

   private McpSchema.CallToolResult executeDDL(String sql, String successMessage) {
      McpSchema.CallToolResult securityCheck = this.checkDangerousOperations(sql);
      if (securityCheck != null) {
         return securityCheck;
      } else {
         try {
            McpSchema.CallToolResult var6;
            try (
               Connection conn = this.getConnection();
               Statement stmt = conn.createStatement();
            ) {
               stmt.setQueryTimeout(60);
               stmt.execute(sql);
               var6 = this.successResult(successMessage);
            }

            return var6;
         } catch (SQLException var12) {
            return !var12.getMessage().toLowerCase().contains("timeout")
                  && !var12.getMessage().toLowerCase().contains("canceling statement")
                  && !var12.getMessage().toLowerCase().contains("query cancelled")
               ? this.errorResult("SQL 错误: " + var12.getMessage())
               : this.errorResult("SQL 执行超时（60秒）: 数据库可能繁忙或存在锁冲突。建议：1) 检查是否有长时间运行的事务 2) 查看 pg_stat_activity 3) 稍后重试");
         }
      }
   }

   private McpSchema.CallToolResult checkDangerousOperations(String sql) {
      if (sql != null && !sql.trim().isEmpty()) {
         String upperSql = sql.trim().toUpperCase();
         String[] dangerousPatterns = new String[]{
            "DROP\\s+DATABASE", "DROP\\s+SCHEMA\\s+", "DROP\\s+USER\\s+", "DROP\\s+ROLE\\s+", "DROP\\s+TABLESPACE", "ALTER\\s+SYSTEM"
         };
         String[] dangerousDescriptions = new String[]{"DROP DATABASE", "DROP SCHEMA", "DROP USER", "DROP ROLE", "DROP TABLESPACE", "ALTER SYSTEM"};

         for (int i = 0; i < dangerousPatterns.length; i++) {
            String regex = "(.*\\b)?" + dangerousPatterns[i] + "(\\b.*)?";
            if (upperSql.matches(regex)) {
               return this.errorResult("错误: 不允许执行 " + dangerousDescriptions[i] + " 操作");
            }
         }

         return null;
      } else {
         return this.errorResult("SQL 语句不能为空");
      }
   }

   private McpSchema.CallToolResult successResult(String message) {
      return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), false);
   }

   private McpSchema.CallToolResult errorResult(String message) {
      return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), true);
   }

   private Connection getConnection() throws SQLException {
      return this.dataSource.getConnection();
   }

   private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
      List<Map<String, Object>> list = new ArrayList<>();
      ResultSetMetaData meta = rs.getMetaData();
      int columnCount = meta.getColumnCount();

      while (rs.next()) {
         Map<String, Object> row = new LinkedHashMap<>();

         for (int i = 1; i <= columnCount; i++) {
            String columnName = meta.getColumnLabel(i);
            Object value = rs.getObject(i);
            row.put(columnName, value);
         }

         list.add(row);
      }

      return list;
   }

   private String quoteIdentifier(String identifier) {
      return "\"" + identifier.replace("\"", "\"\"") + "\"";
   }
}
