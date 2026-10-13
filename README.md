# Database MCP Server

基于 Java 21 和 MCP Java SDK 实现的数据库 MCP 服务，当前支持：

- PostgreSQL
- Oracle
- MySQL

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
- 支持 PostgreSQL / Oracle / MySQL 方言差异
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
  - 热点数据源统计（每次工具调用记一条明细，见 5.3）

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
  数据库类型，支持 `postgres` / `oracle` / `mysql`
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
- MySQL 基础连接不强制绑定数据库名；显式配置 schema 时会执行 `USE schema`

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

### 5.1 SQLite 配置库

HTTP 服务会把所有基础配置和数据源映射持久化到 SQLite。

默认文件：

- `data/database-mcp-config.db`

启动行为：

1. 服务先读取 SQLite 配置库。
2. 配置库首次创建为空。
3. 管理后台修改完成后，会刷新内存中的数据源路由。

### 5.2 执行黑名单（db_execute）

`db_execute` 在真正执行前会按数据源检查一份语句黑名单，命中即拒绝，返回 `Blocked dangerous operation: <语句>`。

规则清单由服务端按数据库类型固定提供（`ExecuteBlacklistRules`），数据源只保存勾选的规则 id。页面上是勾选框，不能自定义正则，避免写出语法错误或灾难回溯的规则。

各类型的默认规则，与黑名单可配置之前的拦截范围完全一致：

| 类型 | 默认拦截 |
| --- | --- |
| PostgreSQL | `DROP DATABASE` `DROP SCHEMA` `DROP USER` `DROP ROLE` `DROP TABLESPACE` `ALTER SYSTEM` |
| Oracle | `DROP DATABASE` `DROP USER` `DROP TABLESPACE` `ALTER SYSTEM` |
| MySQL | `DROP DATABASE` `DROP SCHEMA` `DROP USER` `RESET MASTER` `RESET REPLICA` `SHUTDOWN` |

默认清单不拦截 `DROP TABLE`、`TRUNCATE TABLE`、`DELETE`。这三条在清单里作为可勾选项提供，需要时手动开启。

语义：

- 匹配前语句会去掉注释、压缩空白并转大写，`DROP  TABLE` 与 `DROP\nTABLE` 等价
- 清空黑名单表示该数据源不拦截任何语句，管理后台保存时会二次确认
- 只作用于 `db_execute` 和内部走 DDL 通道的工具，`db_query` 的只读校验不受影响

存储：

- 列 `mcp_datasource.execute_blacklist`，逗号分隔的规则 id
- 启动时自动加列，并把已有数据源回填为其类型的默认规则，已有行为不变
- 空字符串表示不拦截任何语句；`NULL` 表示未配置，沿用 dialect 默认。stdio 与环境变量模式始终是 `NULL`，即使用 dialect 内置默认

相关接口：

- `GET /admin/api/blacklist-rules?type=oracle`：返回该类型的规则清单与默认勾选项
- `PUT /admin/api/datasources/{id}`：`executeBlacklist` 字段接收规则 id 数组，未知 id 直接报 400，不会静默丢弃

### 5.3 热点数据源统计

管理后台第三个 tab 展示哪些数据源被调用得最多。数据来源是每一次 MCP 工具调用，一条一行。

记录内容：`datasourceId`、工具名、耗时、成功/失败、错误摘要、会话 id、时间。**不保存 SQL 语句**，排行用不到，存下来只是体积和隐私负担。

埋点在 `GenericMcpTools` 注册工具时统一包一层，因此：

- 别名工具（`pg_*`）按自己的名字计数
- 调用一个不存在的数据源同样计入，记为失败，`datasourceId` 就是客户端传的那个
- stdio 模式没有落库的地方，不记录，工具行为不变

写入路径：工具调用线程只把记录放进有界队列，后台线程按 `flush-interval` 或攒满 200 条批量写 SQLite。队列满时丢弃并告警一次——丢统计比卡住客户端要得起。

存储：`mcp_tool_invocation` 表，与配置同一个 SQLite 文件，启动时自动建表。

保留：`retention-days` 与 `max-rows` 两个上限，写入线程每小时清理一次，重启后一小时内也会跑第一次。

```yaml
database-mcp:
  http:
    usage-stats:
      enabled: true          # false 时完全不记录，面板保持为空
      retention-days: 30
      max-rows: 200000
      flush-interval: 1s
      queue-capacity: 10000
```

相关接口：

- `GET /admin/api/usage-stats?window=today|7d|30d|all`：按数据源和按工具的排行
- `GET /admin/api/usage-records?datasourceId=&tool=&outcome=&window=&page=&size=`：调用明细，新的在前
- `GET /admin/api/usage-filters`：明细筛选的候选值
- `DELETE /admin/api/usage-records`：清空全部记录

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
```

说明：

- Oracle 当前按 `SID` 组装连接，不支持 `serviceName`
- MySQL 当前按 `jdbc:mysql://host:port` 组装基础连接，`jdbcParams` 会追加为查询参数；默认数据库配置在具体 datasource 的 schema/default database 字段
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
- 勾选每个数据源 `db_execute` 的拦截规则（见 5.2）
- 查看热点数据源：按数据源和工具统计调用次数、耗时与失败（见 5.3）
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
- `db_execute`（受数据源执行黑名单约束，见 5.2）
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

其中三个最常用工具的语义边界如下：

- `db_list_tables`
  用于候选表发现和 schema 探索，不代表已经查到业务数据，也不应直接用于最终业务结论。
- `db_get_ddl`
  用于表结构、列、约束等 DDL 验证，不代表已经查到业务数据，也不应直接用于最终业务结论。
- `db_query`
  用于执行只读 SQL 以获取业务数据和证据。对需要业务结论、统计、趋势分析的问题，应以此工具的结果作为主要依据。

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

- `db_get_ddl` 支持 PostgreSQL / Oracle / MySQL
- `db_compare_schemas` 支持 PostgreSQL 和 Oracle，MySQL 未实现
- 部分索引分析能力依赖具体方言支持
- Oracle、PostgreSQL 和 MySQL 在 schema、标识符大小写、元数据查询 SQL 上存在差异

如果某个方言暂不支持某项能力，工具会返回明确错误信息，而不是静默降级。

Oracle 的 schema 对比按 Oracle 语义解析：`sourceSchema` 和 `targetSchema` 先按大写解析成 `owner`，两个名字解析到同一个 owner 时直接拒绝，避免对同一个 schema 生成自毁脚本。对比范围覆盖表、列、索引、约束、视图、序列；独立函数和过程只比较存在性，不比较包体和触发器，这类差异以 `review manually` 注释输出。生成的同步脚本需要人工复核后再执行。

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
