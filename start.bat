@echo off
set PG_URL=jdbc:postgresql://192.168.2.189:5432/postgres
set PG_USER=postgres
set PG_PASSWORD=e2017hbnr
java -jar "%~dp0target\postgres-mcp-server-1.0.0.jar"
