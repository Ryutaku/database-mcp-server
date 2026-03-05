# PostgreSQL MCP Server

A Java-based MCP (Model Context Protocol) server for PostgreSQL database operations.

It provides a practical toolset for:
- Querying data
- Managing schemas, tables, and indexes
- Executing DDL/DML safely
- Comparing two schemas and generating sync SQL

## Features

This server exposes 18 MCP tools:

| Tool | Purpose |
|---|---|
| `pg_query` | Run read-only `SELECT` / `WITH` queries |
| `pg_switch_schema` | Set active schema (`search_path`) for subsequent requests |
| `pg_list_schemas` | List schemas |
| `pg_create_schema` | Create schema |
| `pg_list_tables` | List tables in a schema |
| `pg_describe_table` | Show table columns and metadata |
| `pg_create_table` | Create table from column definitions |
| `pg_alter_table` | Alter table structure |
| `pg_drop_table` | Drop table |
| `pg_get_ddl` | Generate table DDL |
| `pg_list_indexes` | List indexes |
| `pg_create_index` | Create index |
| `pg_drop_index` | Drop index |
| `pg_analyze_index` | Show index usage stats |
| `pg_execute` | Execute general SQL (DDL/DML) |
| `pg_db_info` | Show database version/size/connections |
| `pg_current_user` | Show current user/session/schema |
| `pg_compare_schemas` | Compare source/target schemas and output sync SQL |

## Tech Stack

- Java 21
- Maven
- MCP Java SDK `0.7.0`
- PostgreSQL JDBC Driver `42.7.3`
- HikariCP connection pool

## Build

```bash
cd postgres-mcp-server
mvn -DskipTests package
```

Output JAR:

```text
target/postgres-mcp-server-1.0.0.jar
```

## Run Locally

```bash
export PG_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=public"
export PG_USER="postgres"
export PG_PASSWORD="your_password"
java -jar target/postgres-mcp-server-1.0.0.jar
```

Windows PowerShell:

```powershell
$env:PG_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=public"
$env:PG_USER="postgres"
$env:PG_PASSWORD="your_password"
java -jar .\target\postgres-mcp-server-1.0.0.jar
```

## MCP Client Setup

### Codex CLI

```powershell
codex mcp add postgres-mcp `
  --env PG_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=public" `
  --env PG_USER="postgres" `
  --env PG_PASSWORD="your_password" `
  -- java -jar "D:\path\to\postgres-mcp-server-1.0.0.jar"
```

### Generic MCP config example

```json
{
  "mcpServers": {
    "postgres-mcp": {
      "command": "java",
      "args": ["-jar", "/path/to/postgres-mcp-server-1.0.0.jar"],
      "env": {
        "PG_URL": "jdbc:postgresql://localhost:5432/postgres?currentSchema=public",
        "PG_USER": "postgres",
        "PG_PASSWORD": "your_password"
      }
    }
  }
}
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `PG_URL` | No | `jdbc:postgresql://localhost:5432/postgres` | PostgreSQL JDBC URL |
| `PG_USER` | No | `postgres` | DB username |
| `PG_PASSWORD` | No | empty | DB password |

Recommended JDBC format:

```text
jdbc:postgresql://host:5432/dbname?currentSchema=your_schema
```

## Runtime Behavior and Safety

- `pg_query` only accepts read-only `SELECT` / `WITH` queries.
- `pg_switch_schema` updates active schema for following requests.
- SQL execution timeout is `60` seconds.
- `pg_execute` (and DDL helpers) block dangerous operations:
  - `DROP DATABASE`
  - `DROP SCHEMA`
  - `DROP USER`
  - `DROP ROLE`
  - `DROP TABLESPACE`
  - `ALTER SYSTEM`
- Results are returned as JSON text payloads.

## Usage Examples

You can ask your MCP client:

- "List all tables in schema `public`."
- "Describe table `orders`."
- "Create an index on `orders(created_at)`."
- "Compare schema `template_schema` and `tenant_schema`."

## Troubleshooting

### 1. Connection failed

Check:
- Host/port reachable from runtime machine
- Username/password permissions
- JDBC URL syntax (`?currentSchema=...`, not `/current_schema=...`)

### 2. MCP handshake failed

Check:
- JAR path is correct
- Java version is 21+
- No old process is holding outdated JAR/config

### 3. Query timeout

Current timeout is 60 seconds. Optimize SQL or reduce result size (for example, add `LIMIT`).

## Project Structure

```text
postgres-mcp-server/
|- pom.xml
|- README.md
`- src/main/java/com/example/mcp/
   |- PostgresMcpServer.java
   |- PgsqlTools.java
   `- SchemaComparator.java
```

## License

MIT