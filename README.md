# Database MCP Server

A Java-based MCP (Model Context Protocol) server for database operations.

The project started as a PostgreSQL MCP server and has now been refactored into a dialect-based architecture that supports:

- PostgreSQL
- Oracle

It provides a practical toolset for:

- querying data
- managing schemas, tables, and indexes
- executing DDL/DML with safety checks
- generating table DDL
- comparing two schemas and generating sync SQL

## Features

This server currently exposes 18 logical MCP tools and registers both:

- database-neutral `db_*` names
- legacy PostgreSQL-compatible `pg_*` aliases

Examples:

- `db_query` and `pg_query`
- `db_list_tables` and `pg_list_tables`
- `db_compare_schemas` and `pg_compare_schemas`

The table below lists the logical tool set:

| Tool | Purpose |
|---|---|
| `db_query` | Run read-only `SELECT` / `WITH` queries |
| `db_switch_schema` | Set active schema for subsequent requests |
| `db_list_schemas` | List schemas |
| `db_create_schema` | Create schema |
| `db_list_tables` | List tables in a schema |
| `db_describe_table` | Show table columns and metadata |
| `db_create_table` | Create table from column definitions |
| `db_alter_table` | Alter table structure |
| `db_drop_table` | Drop table |
| `db_get_ddl` | Generate table DDL |
| `db_list_indexes` | List indexes |
| `db_create_index` | Create index |
| `db_drop_index` | Drop index |
| `db_analyze_index` | Show index usage stats |
| `db_execute` | Execute general SQL (DDL/DML) |
| `db_info` | Show database info |
| `db_current_user` | Show current user/session/schema |
| `db_compare_schemas` | Compare source/target schemas and output sync SQL |

## Database Support

### PostgreSQL

Supported:

- all current `db_*` tools
- schema compare and sync SQL generation
- table DDL generation
- index analysis

### Oracle

Supported:

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

Currently limited or unsupported:

- `db_create_schema`
  Oracle schema semantics do not match PostgreSQL schema creation
- `db_analyze_index`
  not implemented because Oracle index usage visibility is privilege-sensitive
- routine compare in `db_compare_schemas`
  currently reported conservatively rather than doing deep source diffing

## Tech Stack

- Java 21
- Maven
- MCP Java SDK `0.7.0`
- PostgreSQL JDBC Driver `42.7.3`
- HikariCP connection pool

Note:

- Oracle support is implemented in code through the dialect architecture
- add an Oracle JDBC dependency in your build/runtime packaging strategy if you want to run with `DB_TYPE=oracle`

## Build

```bash
cd database-mcp-server
mvn -DskipTests package
```

Output JAR:

```text
target/database-mcp-server-1.0.0.jar
```

## Run Locally

### Unified environment variables

Recommended variables:

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_TYPE` | No | `postgres` | Database type: `postgres` or `oracle` |
| `DB_URL` | No | engine-specific default | JDBC URL |
| `DB_USER` | No | engine-specific default | DB username |
| `DB_PASSWORD` | No | empty | DB password |
| `DB_SCHEMA` | No | `public` for PostgreSQL | Default active schema |

Legacy PostgreSQL variables are still supported for backward compatibility:

- `PG_URL`
- `PG_USER`
- `PG_PASSWORD`

### PostgreSQL example

```bash
export DB_TYPE="postgres"
export DB_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=public"
export DB_USER="postgres"
export DB_PASSWORD="your_password"
export DB_SCHEMA="public"
java -jar target/database-mcp-server-1.0.0.jar
```

Windows PowerShell:

```powershell
$env:DB_TYPE="postgres"
$env:DB_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=public"
$env:DB_USER="postgres"
$env:DB_PASSWORD="your_password"
$env:DB_SCHEMA="public"
java -jar .\target\database-mcp-server-1.0.0.jar
```

### Oracle example

```bash
export DB_TYPE="oracle"
export DB_URL="jdbc:oracle:thin:@localhost:1521/FREEPDB1"
export DB_USER="system"
export DB_PASSWORD="your_password"
export DB_SCHEMA="YOUR_SCHEMA"
java -jar target/database-mcp-server-1.0.0.jar
```

Windows PowerShell:

```powershell
$env:DB_TYPE="oracle"
$env:DB_URL="jdbc:oracle:thin:@localhost:1521/FREEPDB1"
$env:DB_USER="system"
$env:DB_PASSWORD="your_password"
$env:DB_SCHEMA="YOUR_SCHEMA"
java -jar .\target\database-mcp-server-1.0.0.jar
```

## MCP Client Setup

### Codex CLI

PostgreSQL example:

```powershell
codex mcp add postgres-mcp `
  --env DB_TYPE="postgres" `
  --env DB_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=public" `
  --env DB_USER="postgres" `
  --env DB_PASSWORD="your_password" `
  --env DB_SCHEMA="public" `
  -- java -jar "D:\path\to\database-mcp-server-1.0.0.jar"
```

Oracle example:

```powershell
codex mcp add oracle-mcp `
  --env DB_TYPE="oracle" `
  --env DB_URL="jdbc:oracle:thin:@localhost:1521/FREEPDB1" `
  --env DB_USER="system" `
  --env DB_PASSWORD="your_password" `
  --env DB_SCHEMA="YOUR_SCHEMA" `
  -- java -jar "D:\path\to\database-mcp-server-1.0.0.jar"
```

### Generic MCP config example

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

## Runtime Behavior and Safety

- `db_query` only accepts read-only `SELECT` / `WITH` queries.
- `pg_query` is kept as a backward-compatible alias of `db_query`.
- active schema is applied per connection using dialect-specific session logic.
- SQL execution timeout is `60` seconds.
- `db_execute` and DDL helpers block dangerous operations through per-dialect safety policies.
- results are returned as JSON text payloads.

### Dangerous operations blocked by default

PostgreSQL:

- `DROP DATABASE`
- `DROP SCHEMA`
- `DROP USER`
- `DROP ROLE`
- `DROP TABLESPACE`
- `ALTER SYSTEM`

Oracle:

- `DROP DATABASE`
- `DROP USER`
- `DROP TABLESPACE`
- `ALTER SYSTEM`

## Usage Examples

You can ask your MCP client:

- "List all tables in schema `public`."
- "Describe table `orders`."
- "Create an index on `orders(created_at)`."
- "Generate the DDL for table `orders`."
- "Compare schema `template_schema` and `tenant_schema`."

## Troubleshooting

### 1. Connection failed

Check:

- host/port reachable from runtime machine
- username/password permissions
- JDBC URL syntax
- Oracle driver availability if running with `DB_TYPE=oracle`

### 2. MCP handshake failed

Check:

- JAR path is correct
- Java version is 21+
- no old process is holding outdated JAR/config

### 3. Query timeout

Current timeout is 60 seconds. Optimize SQL or reduce result size.

### 4. Oracle metadata access failed

Check:

- the connected user can access the needed `ALL_*` dictionary views
- the target schema/owner exists
- object names are provided with the expected Oracle casing when relevant

## Architecture

The server now uses a dialect-based architecture:

- `bootstrap`
  generic server startup
- `config`
  runtime config parsing
- `dialect`
  PostgreSQL and Oracle behavior
- `execution`
  connection and JDBC execution helpers
- `schema`
  schema snapshot, diff, and sync SQL generation
- `tools`
  MCP tool registration and handlers

Detailed design notes:

- [oracle-support-design.md](./docs/oracle-support-design.md)

## Project Structure

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
