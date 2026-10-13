package com.hbnrtech.mcp.schema.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hbnrtech.mcp.schema.ColumnDef;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OracleSchemaSnapshotProviderTest {
   private final OracleSchemaSnapshotProvider provider = new OracleSchemaSnapshotProvider();

   @Test
   void rendersTableDdlFromFetchedMetadata() {
      Map<String, ColumnDef> columns = new LinkedHashMap<>();
      columns.put("ID", new ColumnDef("ID", "NUMBER(18)", false, null, 1));
      columns.put("NAME", new ColumnDef("NAME", "VARCHAR2(80)", true, "'anonymous'", 2));

      String ddl = provider.renderTableDdl("APP", "USERS", columns, "User accounts", Map.of("ID", "Identifier"));

      assertEquals("""
         CREATE TABLE "APP"."USERS" (
             "ID" NUMBER(18) NOT NULL,
             "NAME" VARCHAR2(80) DEFAULT 'anonymous'
         );

         COMMENT ON TABLE "APP"."USERS" IS 'User accounts';
         COMMENT ON COLUMN "APP"."USERS"."ID" IS 'Identifier';""", ddl);
   }

   @Test
   void rendersIndexDefinitionInTheSameShapeAsTheRemovedListaggSql() {
      assertEquals(
         "CREATE INDEX APP.IDX_USERS_NAME ON APP.USERS (NAME, ID)",
         OracleSchemaSnapshotProvider.renderIndexDefinition("APP", "IDX_USERS_NAME", "APP", "USERS", false, List.of("NAME", "ID"))
      );
      assertEquals(
         "CREATE UNIQUE INDEX APP.UX_USERS_ID ON APP.USERS (ID)",
         OracleSchemaSnapshotProvider.renderIndexDefinition("APP", "UX_USERS_ID", "APP", "USERS", true, List.of("ID"))
      );
   }

   @Test
   void rendersConstraintDefinitionsForEverySupportedType() {
      assertEquals("PRIMARY KEY (ID)", OracleSchemaSnapshotProvider.renderConstraintDefinition("P", List.of("ID"), null, null, null));
      assertEquals("UNIQUE (NAME)", OracleSchemaSnapshotProvider.renderConstraintDefinition("U", List.of("NAME"), null, null, null));
      assertEquals(
         "FOREIGN KEY (DEPT_ID) REFERENCES APP.DEPTS (ID)",
         OracleSchemaSnapshotProvider.renderConstraintDefinition("R", List.of("DEPT_ID"), "APP", "DEPTS", List.of("ID"))
      );
   }

   @Test
   void rendersCrossSchemaForeignKeyReferences() {
      assertEquals(
         "FOREIGN KEY (DEPT_ID) REFERENCES OTHER.DEPTS (ID)",
         OracleSchemaSnapshotProvider.renderConstraintDefinition("R", List.of("DEPT_ID"), "OTHER", "DEPTS", List.of("ID"))
      );
   }

   @Test
   void readsViewTextBeyondTheDriverFetchLimit() throws SQLException {
      String viewText = "SELECT " + "x".repeat(200_000);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getCharacterStream("text")).thenReturn(new StringReader(viewText));

      assertEquals(viewText, OracleSchemaSnapshotProvider.readLongText(rs, "text"));
   }

   @Test
   void capsOversizedMetadataText() throws SQLException {
      String viewText = "y".repeat(OracleSchemaSnapshotProvider.MAX_METADATA_TEXT_CHARS + 1000);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getCharacterStream("text")).thenReturn(new StringReader(viewText));

      assertEquals(OracleSchemaSnapshotProvider.MAX_METADATA_TEXT_CHARS, OracleSchemaSnapshotProvider.readLongText(rs, "text").length());
   }

   @Test
   void fallsBackToGetStringWhenNoStreamIsAvailable() throws SQLException {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getCharacterStream("text")).thenReturn(null);
      when(rs.getString("text")).thenReturn("short view text");

      assertEquals("short view text", OracleSchemaSnapshotProvider.readLongText(rs, "text"));
   }
}
