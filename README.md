# Database MCP Server

基于 Java 21 和 MCP Java SDK 实现的数据库 MCP 服务，当前支持：

- PostgreSQL
- Oracle

这个仓库已经拆分为多模块结构，既可以通过 `stdio` 方式接入本地 MCP 客户端，也可以通过 `HTTP` 方式提供可管理的服务端。

## 1. 项目结构

- `database-mcp-core`
  共享核心能力，包括运行时装配、数据库方言、连接管理、Schema 对比、工具注册等。
- `database-mcp-stdio`
  基于标准输入输出的 MCP Server，适合本地桌面客户端或命令行宿主进程。
- `database-mcp-http`
  基于 Spring Boot 3 的 HTTP 版 MCP Server，包含管理后台、SQLite 配置存储、API Key 校验等能力。

## 2. 主要能力

- 按 `datasourceId` 路由到不同数据库连接
- 支持 PostgreSQL / Oracle 方言差异
- 提供常见数据库操作工具：
  - 查询
  - DDL / DML 执行
  - Schema、表、索引管理
  - 当前用户和数据库信息查询
  - PostgreSQL 的表 DDL 导出
  - PostgreSQL 的 Schema 对比与同步 SQL 生成
- HTTP 模式下支持：
  - SQLite 持久化配置
  - Web 管理后台
  - API Key 鉴权
  - 管理口令保护

## 3. 构建

要求：

- JDK 21
- Maven 3.9+

在仓库根目录执行：

```powershell
mvn clean package
```

只构建 HTTP 模块：

```powershell
mvn clean package -pl database-mcp-http -am
```

只构建 stdio 模块：

```powershell
mvn clean package -pl database-mcp-stdio -am
```

常见产物：

- `database-mcp-core\target\database-mcp-core-1.0.0.jar`
- `database-mcp-stdio\target\database-mcp-stdio-1.0.0.jar`
- `database-mcp-http\target\database-mcp-http-1.0.0.jar`

## 4. 运行方式

### 4.1 `stdio` 模式

`stdio` 模式默认只加载一个数据源，配置来自环境变量。

启动方式：

```powershell
java -jar .\database-mcp-stdio\target\database-mcp-stdio-1.0.0.jar
```

支持的环境变量：

- `DB_TYPE`
  数据库类型，支持 `postgres` / `oracle`
- `DB_URL`
  JDBC 地址
- `DB_USER`
  用户名
- `DB_PASSWORD`
  密码
- `DB_SCHEMA`
  默认 Schema

兼容旧变量：

- `PG_URL`
- `PG_USER`
- `PG_PASSWORD`
- `PG_SCHEMA`

说明：

- `DB_*` 优先级高于 `PG_*`
- `stdio` 模式会把这个单一配置映射成默认数据源，`datasourceId` 固定可理解为 `default`
- PostgreSQL 未显式配置 schema 时默认使用 `public`
- Oracle 未显式配置 schema 时不强制默认值

### 4.2 HTTP 模式

启动方式：

```powershell
java -jar .\database-mcp-http\target\database-mcp-http-1.0.0.jar
```

默认服务：

- HTTP 端口：`8080`
- MCP Endpoint：`/mcp`
- 管理页：`http://localhost:8080/admin/index.html`
- 管理 API 前缀：`/admin/api`

## 5. HTTP 配置模型

HTTP 模式不是直接把 JDBC 连接暴露给客户端，而是通过 `datasourceId` 做二级路由：

`datasourceId -> 数据源配置 -> 基础 JDBC 配置 + schema`

这样做的目的：

- 客户端只传 `datasourceId`
- 服务端统一维护真实数据库连接信息
- 多个数据源可以复用同一套基础 JDBC 地址配置
- 数据源可以绑定各自独立的用户名、密码、默认 Schema

### 5.1 两层配置

HTTP 服务维护两类配置：

- 基础 JDBC 配置 `base-jdbc-configs`
- 数据源配置 `datasources`

基础 JDBC 配置负责：

- 数据库类型
- 主机
- 端口
- 数据库名或 Oracle SID
- 可选 JDBC 参数

数据源配置负责：

- `datasourceId`
- 引用哪个 `baseConfigId`
- 用户名
- 密码
- 默认 Schema

### 5.2 SQLite 配置库

HTTP 服务会把配置持久化到 SQLite。

默认文件：

- `data/database-mcp-config.db`

启动行为：

1. 服务先读取 SQLite 配置库。
2. 如果配置库为空，则使用 `application.yml` 中的样例配置初始化。
3. 初始化后，后续修改以 SQLite 中的数据为准。
4. 管理后台修改完成后，会刷新内存中的数据源路由。

## 6. HTTP 配置示例

主配置文件：

- `database-mcp-http/src/main/resources/application.yml`

示例：

```yaml
server:
  port: 8080

database-mcp:
  http:
    endpoint: /mcp
    keep-alive-interval: 60s

    api-key-enabled: false
    api-key-header: X-API-Key
    api-key-secret: change-me-secret
    api-key-ttl-seconds: 300
    api-key-allowed-clock-skew-seconds: 30

    admin-api-base-path: /admin/api
    admin-password-header: X-Admin-Password
    admin-password: ""

    config-db-path: data/database-mcp-config.db

    base-jdbc-configs:
      pg-platform:
        type: postgres
        host: 127.0.0.1
        port: 5432
        database-name: platform
        jdbc-params: applicationName=database-mcp-http

      oracle-main:
        type: oracle
        host: 127.0.0.1
        port: 1521
        sid: ORCL

    datasources:
      order-db:
        base-config-id: pg-platform
        username: order_user
        password: change-me-order-password
        schema: order_center

      report-db:
        base-config-id: oracle-main
        username: report_user
        password: change-me-report-password
        schema: REPORT_APP
```

说明：

- Oracle 当前按 `SID` 组装连接，不支持 `serviceName`
- `admin-password` 留空时，默认管理口令为服务端当天日期，格式为 `yyyy-MM-dd`
- `api-key-enabled=false` 时，MCP 接口不校验 API Key

## 7. 管理后台

管理后台页面：

- `http://localhost:8080/admin/index.html`

默认请求头：

- 管理口令头：`X-Admin-Password`
- API Key 头：`X-API-Key`

管理后台主要用途：

- 维护基础 JDBC 配置
- 维护数据源配置
- 查看和修改运行时路由数据

相关后端文件：

- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/admin/AdminConfigController.java`
- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/admin/RuntimeConfigurationService.java`
- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/admin/SqliteConfigRepository.java`

相关前端文件：

- `database-mcp-http/src/main/resources/static/admin/index.html`
- `database-mcp-http/src/main/resources/static/admin/admin.css`
- `database-mcp-http/src/main/resources/static/admin/admin.js`

## 8. API Key 机制

API Key 格式：

```text
clientId.timestamp.nonce.signature
```

签名算法：

```text
Base64Url(HMAC_SHA256(secret, clientId.timestamp.nonce))
```

校验规则：

- 签名必须匹配
- 时间戳必须处于允许窗口内
- API Key 不绑定具体数据源

相关实现：

- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/config/ApiKeyAuthenticationFilter.java`
- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/config/ApiKeySignatureService.java`

## 9. MCP 工具说明

核心工具名以 `db_` 开头，例如：

- `db_query`
- `db_execute`
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
- `db_analyze_index`
- `db_info`
- `db_current_user`
- `db_compare_schemas`

同时提供一套兼容别名：

- `pg_query`
- `pg_list_tables`
- `pg_describe_table`
- `pg_db_info`

这类别名本质上还是同一套实现，只是为了兼容旧客户端命名。

### 9.1 统一入参规则

所有工具都会自动追加 `datasourceId` 参数。

例如：

```json
{
  "datasourceId": "order-db",
  "tableName": "t_order"
}
```

读操作示例：

```json
{
  "datasourceId": "order-db",
  "sql": "select now()"
}
```

说明：

- 客户端必须传 `datasourceId`
- `schema` 多数场景可省略，服务端会按以下顺序决定：
  1. 请求显式传入的 `schema`
  2. 当前会话通过 `db_switch_schema` 切换后的 schema
  3. 数据源默认 schema
  4. PostgreSQL 默认 `public`

## 10. 数据库能力差异

并不是所有工具对所有数据库都完全等价。

当前实现里：

- `db_get_ddl` 仅对 PostgreSQL 实现
- `db_compare_schemas` 仅对 PostgreSQL 实现
- 部分索引分析能力依赖具体方言支持
- Oracle 和 PostgreSQL 在 schema、标识符大小写、元数据查询 SQL 上存在差异

如果某个方言暂不支持某项能力，工具会返回明确错误信息，而不是静默降级。

## 11. 关键源码位置

- `database-mcp-core/src/main/java/com/hbnrtech/mcp/bootstrap/DatabaseMcpRuntimeFactory.java`
- `database-mcp-core/src/main/java/com/hbnrtech/mcp/execution/DatasourceRegistry.java`
- `database-mcp-core/src/main/java/com/hbnrtech/mcp/execution/ConnectionManager.java`
- `database-mcp-core/src/main/java/com/hbnrtech/mcp/tools/GenericMcpTools.java`
- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/config/DatabaseMcpHttpProperties.java`
- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/config/McpHttpServerConfiguration.java`

## 12. 测试

执行全部测试：

```powershell
mvn test
```

## 13. License

MIT
