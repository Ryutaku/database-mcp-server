#!/bin/bash
export PG_URL="jdbc:postgresql://192.168.2.189:5432/postgres"
export PG_USER="postgres"
export PG_PASSWORD="e2017hbnr"
java -jar "$(dirname "$0")/target/postgres-mcp-server-1.0.0.jar"
