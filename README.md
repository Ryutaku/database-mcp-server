# Database MCP Server

一个基于 Java 的数据库 MCP（Model Context Protocol）服务端，提供统一的数据库访问与结构管理能力。

项目最初面向 PostgreSQL，后续已重构为“方言驱动”的架构，目前支持：

- PostgreSQL
- Oracle

它适合挂到支持 MCP 的客户端上，让大模型通过标准工具调用数据库能力，例如：

- 执行只读查询
- 管理 schema、表、索引
- 执行带安全校验的 DDL / DML
- 生成表 DDL
- 对比两个 schema 并生成同步 SQL

## 项目特点

- 使用统一的 `db_*` 工具名，便于跨数据库复用
- 保留 `pg_*` 兼容别名，降低旧客户端迁移成本
- 基于数据库方言隔离 PostgreSQL 和 Oracle 的差异
- 内置 SQL 安全策略，阻止高风险操作
- 支持 schema 快照、差异比对和同步脚本生成

当前逻辑上一共暴露 18 个核心工具，同时自动注册对应的 PostgreSQL 兼容别名。

例如：

- `db_query` 与 `pg_query`
- `db_list_tables` 与 `pg_list_tables`
- `db_compare_schemas` 与 `pg_compare_schemas`

## 工具列表

| 工具名 | 说明 |
|---|---|
| `db_query` | 执行只读 `SELECT` / `WITH` 查询 |
| `db_switch_schema` | 切换后续请求使用的活动 schema |
| `db_list_schemas` | 列出 schema |
| `db_create_schema` | 创建 schema |
| `db_list_tables` | 列出指定 schema 下的表 |
| `db_describe_table` | 查看表结构与元数据 |
| `db_create_table` | 根据列定义创建表 |
| `db_alter_table` | 修改表结构 |
| `db_drop_table` | 删除表 |
| `db_get_ddl` | 生成表 DDL |
| `db_list_indexes` | 列出索引 |
| `db_create_index` | 创建索引 |
| `db_drop_index` | 删除索引 |
| `db_analyze_index` | 分析索引使用情况 |
| `db_execute` | 执行 DDL / DML SQL |
| `db_info` | 查看数据库信息 |
| `db_current_user` | 查看当前用户、会话和 schema 信息 |
| `db_compare_schemas` | 对比两个 schema 并生成同步 SQL |

## 数据库支持情况

### PostgreSQL

支持全部 `db_*` 核心工具，包括：

- schema 对比与同步 SQL 生成
- 表 DDL 生成
- 索引分析

### Oracle

已支持：

- `db_query`
- `db_switch_schema`
- `db_list_schemas`
- `db_list_tables`
- `db_describe_table`
- `db_create_table`
- `db_alter_table`
- `db_drop_table`
- `db_get_ddl`
- `db_list_indexes`
- `db_create_index`
- `db_drop_index`
- `db_execute`
- `db_info`
- `db_current_user`
- `db_compare_schemas`

当前限制：

- `db_create_schema`
  Oracle 中 schema 与用户语义绑定，不等价于 PostgreSQL 的独立 schema 创建
- `db_analyze_index`
  尚未实现，主要因为 Oracle 的索引使用统计对权限较敏感
- `db_compare_schemas` 中的 routine 深度比对
  当前以保守方式报告差异，未做完整源码级比较

## 技术栈

- Java 21
- Maven
- MCP Java SDK `0.17.2`
- PostgreSQL JDBC Driver `42.7.3`
- Oracle JDBC Driver `23.3.0.23.09`
- Jackson `2.17.0`
- HikariCP `5.1.0`

## 构建

在项目根目录执行：

```bash
mvn -DskipTests package
```

构建产物：

```text
target/database-mcp-server-1.0.0.jar
```

说明：

- 当前 `pom.xml` 已包含 PostgreSQL 与 Oracle JDBC 依赖
- 使用 Shade Plugin 打包为可直接运行的 fat JAR
- Manifest 主类为 `com.example.mcp.DatabaseMcpServer`

## 运行方式

### 统一环境变量

推荐使用以下环境变量：

| 变量名 | 是否必填 | 默认值 | 说明 |
|---|---|---|---|
| `DB_TYPE` | 否 | `postgres` | 数据库类型，可选 `postgres` 或 `oracle` |
| `DB_URL` | 否 | 按数据库类型自动补默认值 | JDBC 连接串 |
| `DB_USER` | 否 | 按数据库类型自动补默认值 | 数据库用户名 |
| `DB_PASSWORD` | 否 | 空字符串 | 数据库密码 |
| `DB_SCHEMA` | 否 | PostgreSQL 默认为 `public` | 默认活动 schema |

兼容旧版 PostgreSQL 配置：

- `PG_URL`
- `PG_USER`
- `PG_PASSWORD`

当未显式提供 `DB_*` 且存在 `PG_URL` 时，程序会自动按 PostgreSQL 模式兼容启动。

### PostgreSQL 示例

```powershell
$env:DB_TYPE="postgres"
$env:DB_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=public"
$env:DB_USER="postgres"
$env:DB_PASSWORD="your_password"
$env:DB_SCHEMA="public"
java -jar .\target\database-mcp-server-1.0.0.jar
```

### Oracle 示例

```powershell
$env:DB_TYPE="oracle"
$env:DB_URL="jdbc:oracle:thin:@localhost:1521/FREEPDB1"
$env:DB_USER="system"
$env:DB_PASSWORD="your_password"
$env:DB_SCHEMA="YOUR_SCHEMA"
java -jar .\target\database-mcp-server-1.0.0.jar
```

## MCP 客户端接入

### Codex CLI 示例

PostgreSQL：

```powershell
codex mcp add postgres-mcp `
  --env DB_TYPE="postgres" `
  --env DB_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=public" `
  --env DB_USER="postgres" `
  --env DB_PASSWORD="your_password" `
  --env DB_SCHEMA="public" `
  -- java -jar "D:\path\to\database-mcp-server-1.0.0.jar"
```

Oracle：

```powershell
codex mcp add oracle-mcp `
  --env DB_TYPE="oracle" `
  --env DB_URL="jdbc:oracle:thin:@localhost:1521/FREEPDB1" `
  --env DB_USER="system" `
  --env DB_PASSWORD="your_password" `
  --env DB_SCHEMA="YOUR_SCHEMA" `
  -- java -jar "D:\path\to\database-mcp-server-1.0.0.jar"
```

### 通用 MCP 配置示例

```json
{
  "mcpServers": {
    "database-mcp": {
      "command": "java",
      "args": ["-jar", "/path/to/database-mcp-server-1.0.0.jar"],
      "env": {
        "DB_TYPE": "postgres",
        "DB_URL": "jdbc:postgresql://localhost:5432/postgres?currentSchema=public",
        "DB_USER": "postgres",
        "DB_PASSWORD": "your_password",
        "DB_SCHEMA": "public"
      }
    }
  }
}
```

## 运行行为与安全策略

- `db_query` 仅允许只读 `SELECT` / `WITH` 查询
- `pg_query` 是 `db_query` 的兼容别名
- 活动 schema 会在每次获取连接时按数据库方言应用到当前会话
- SQL 执行超时时间为 `60` 秒
- `db_execute` 与 DDL 类工具都会经过方言级安全策略校验
- 返回结果以 JSON 文本或文本报告形式返回给 MCP 客户端

### 默认拦截的危险操作

PostgreSQL：

- `DROP DATABASE`
- `DROP SCHEMA`
- `DROP USER`
- `DROP ROLE`
- `DROP TABLESPACE`
- `ALTER SYSTEM`

Oracle：

- `DROP DATABASE`
- `DROP USER`
- `DROP TABLESPACE`
- `ALTER SYSTEM`

## 使用示例

你可以在 MCP 客户端中直接提类似的问题：

- “列出 `public` schema 下的所有表”
- “查看 `orders` 表结构”
- “给 `orders(created_at)` 创建索引”
- “生成 `orders` 表的 DDL”
- “对比 `template_schema` 和 `tenant_schema` 的差异”

## 常见问题

### 1. 数据库连接失败

建议检查：

- 数据库地址和端口是否可达
- 用户名和密码是否正确
- JDBC URL 是否符合对应数据库语法
- Oracle 模式下驱动和目标实例是否可用

### 2. MCP 握手失败

建议检查：

- JAR 路径是否正确
- Java 版本是否为 21 或更高
- 是否仍有旧进程占用了旧版 JAR 或旧配置

### 3. 查询超时

当前超时时间为 `60` 秒。可以优先优化 SQL、增加过滤条件或缩小结果集。

### 4. Oracle 元数据访问失败

建议检查：

- 当前用户是否有访问所需 `ALL_*` 视图的权限
- 目标 schema / owner 是否存在
- 对象名大小写是否符合 Oracle 期望

## 架构说明

当前项目采用基于方言的分层结构：

- `bootstrap`
  服务启动与 MCP Server 组装
- `config`
  环境变量解析与运行时配置归一化
- `dialect`
  PostgreSQL / Oracle 方言能力、SQL 模板和安全策略
- `execution`
  连接管理与 JDBC 执行封装
- `schema`
  schema 快照、结构比对和同步脚本生成
- `tools`
  MCP 工具定义、别名生成和处理函数实现

设计说明文档：

- [docs/oracle-support-design.md](./docs/oracle-support-design.md)

## 项目结构

```text
database-mcp-server/
|- pom.xml
|- README.md
|- docs/
|  `- oracle-support-design.md
`- src/main/java/com/example/mcp/
   |- DatabaseMcpServer.java
   |- bootstrap/
   |- config/
   |- dialect/
   |- execution/
   |- schema/
   `- tools/
```

## License

MIT
