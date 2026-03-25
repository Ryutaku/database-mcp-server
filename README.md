# Database MCP Server

Database MCP Server implemented with Java 21 and the MCP Java SDK.

Supported databases:

- PostgreSQL
- Oracle

Modules:

- `database-mcp-core`: shared runtime, dialects, schema logic, tool registry
- `database-mcp-stdio`: stdio transport
- `database-mcp-http`: Spring Boot 3.x HTTP transport

## Build

Use this Maven executable:

```powershell
& "D:\Programs\apache-maven\bin\mvn.cmd" package -DskipTests
```

Build only the HTTP module:

```powershell
& "D:\Programs\apache-maven\bin\mvn.cmd" package -DskipTests -pl database-mcp-http -am
```

## Artifacts

- `database-mcp-core\target\database-mcp-core-1.0.0.jar`
- `database-mcp-stdio\target\database-mcp-stdio-1.0.0.jar`
- `database-mcp-http\target\database-mcp-http-1.0.0.jar`

## HTTP service model

The HTTP module is now based on these rules:

1. The client sends only `datasourceId` in MCP tool arguments.
2. The server resolves `datasourceId` to a datasource definition.
3. Each datasource definition references a base JDBC configuration.
4. The datasource itself stores the schema to use.
5. API keys are not bound to any datasource.
6. API keys are disabled by default and can be enabled when needed.

This means the runtime routing is:

`datasourceId -> datasource config -> base JDBC config + schema`

## SQLite config store

The HTTP service persists config into SQLite.

Default SQLite file:

- `data/database-mcp-config.db`

Two config layers are stored:

- base JDBC configs
- datasources

Base JDBC config contains shared connection details such as:

- database type
- host
- port
- database name or Oracle SID
- optional JDBC params

Datasource config contains:

- `datasourceId`
- referenced `baseConfigId`
- username
- password
- schema

## Default config file

Main config file:

- `database-mcp-http/src/main/resources/application.yml`

Startup behavior:

1. The service reads the SQLite store.
2. If the store is empty, it seeds SQLite from `application.yml`.
3. After that, the admin page can manage base JDBC configs and datasources.
4. Runtime datasource routing is refreshed in memory after each change.

## Admin page

Admin page URL:

- `http://localhost:8080/admin/index.html`

Admin API base path:

- `/admin/api`

Admin password header:

- `X-Admin-Password`

Main admin backend files:

- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/admin/AdminConfigController.java`
- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/admin/RuntimeConfigurationService.java`
- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/admin/SqliteConfigRepository.java`

Admin frontend files:

- `database-mcp-http/src/main/resources/static/admin/index.html`
- `database-mcp-http/src/main/resources/static/admin/admin.css`
- `database-mcp-http/src/main/resources/static/admin/admin.js`

## API key algorithm

API key format:

```text
clientId.timestamp.nonce.signature
```

Signature:

```text
Base64Url(HMAC_SHA256(secret, clientId.timestamp.nonce))
```

Validation rules:

- signature must match
- timestamp must be inside the allowed time window
- datasource binding is not required

Related files:

- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/config/ApiKeyAuthenticationFilter.java`
- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/config/ApiKeySignatureService.java`

## Example config

```yaml
server:
  port: 8080

database-mcp:
  http:
    endpoint: /mcp
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
        host: localhost
        port: 5432
        database-name: platform
        jdbc-params: applicationName=database-mcp-http
      oracle-report:
        type: oracle
        host: localhost
        port: 1521
        sid: ORCL
    datasources:
      order-db:
        base-config-id: pg-platform
        username: order_user
        password: change-me-order-password
        schema: order_center
      billing-db:
        base-config-id: pg-platform
        username: billing_user
        password: change-me-billing-password
        schema: billing_center
      report-db:
        base-config-id: oracle-report
        username: report_user
        password: change-me-report-password
        schema: REPORT_APP
```

## MCP tool call shape

Tool names remain `db_*`.

Example:

```json
{
  "datasourceId": "order-db",
  "sql": "select now()"
}
```

The client does not need to send `schema`.
The client does not need to send a separate caller identity.
The admin page default password is the server date in `yyyy-MM-dd` format when `admin-password` is blank.

## Key source files

- `database-mcp-core/src/main/java/com/hbnrtech/mcp/tools/GenericMcpTools.java`
- `database-mcp-core/src/main/java/com/hbnrtech/mcp/execution/DatasourceRegistry.java`
- `database-mcp-core/src/main/java/com/hbnrtech/mcp/execution/ConnectionManager.java`
- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/config/McpHttpServerConfiguration.java`
- `database-mcp-http/src/main/java/com/hbnrtech/mcp/http/config/DatabaseMcpHttpProperties.java`

## License

MIT
