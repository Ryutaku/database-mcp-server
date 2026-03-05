# PostgreSQL MCP Server

为 Gemini CLI 提供 PostgreSQL 数据库访问能力的 MCP Server。

## 功能特性

- **pg_query**: 执行 SELECT 查询
- **pg_describe_table**: 获取表结构信息
- **pg_list_tables**: 列出所有表
- **pg_execute**: 执行 INSERT/UPDATE/DELETE 等数据修改操作

## 技术栈

- JDK 21
- Maven
- MCP Java SDK 0.7.0
- PostgreSQL JDBC Driver 42.7.3

## 构建

```bash
# 进入项目目录
cd postgres-mcp-server

# 使用 Maven 打包
mvn clean package

# 生成的 JAR 文件位置
target/postgres-mcp-server-1.0.0.jar
```

## 配置 Gemini CLI

编辑 `~/.gemini/config.yaml`：

```yaml
mcpServers:
  postgres:
    command: "java"
    args:
      - "-jar"
      - "/path/to/postgres-mcp-server-1.0.0.jar"
    env:
      PG_URL: "jdbc:postgresql://localhost:5432/mydb"
      PG_USER: "postgres"
      PG_PASSWORD: "your_password"
```

### 环境变量说明

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| PG_URL | JDBC 连接 URL | jdbc:postgresql://localhost:5432/postgres |
| PG_USER | 数据库用户名 | postgres |
| PG_PASSWORD | 数据库密码 | （空） |

## 使用示例

配置完成后，在 Gemini CLI 中可以直接使用：

```
> 查询 users 表中的所有数据

> 查看 orders 表的表结构

> 列出数据库中的所有表

> 插入一条新用户记录
```

## 安全说明

- `pg_query` 仅允许执行 SELECT 语句
- `pg_execute` 禁止执行 DROP DATABASE/SCHEMA 操作
- 建议为 MCP Server 配置具有适当权限的数据库用户
- 生产环境建议使用只读用户

## 项目结构

```
postgres-mcp-server/
├── pom.xml                           # Maven 配置
├── README.md                         # 本文件
└── src/main/java/com/example/mcp/
    ├── PostgresMcpServer.java        # 主入口
    └── PgsqlTools.java               # MCP Tools 实现
```

## 故障排查

### 连接失败
- 检查 PG_URL 是否正确
- 确认 PostgreSQL 服务是否运行
- 验证用户名密码
- 检查防火墙设置

### MCP Server 无法启动
- 确认 JDK 21 已安装：`java -version`
- 检查 JAR 文件路径是否正确
- 查看 Gemini CLI 日志

## License

MIT