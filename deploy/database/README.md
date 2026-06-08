# Database Migrations

## H2 development database

Spring Boot runs the SQL files under
`backend-spring/src/main/resources/db/migration/h2` through Flyway before JPA
starts. Hibernate uses `ddl-auto=validate`; it no longer creates or updates
tables.

The `dev` profile enables `baseline-on-migrate` so an existing local database
created by older project versions is adopted at version `0`, then upgraded to
the current migration version. This switch is intentionally disabled by
default outside development.

## DM8 production database

Flyway does not list Dameng DM8 as an officially supported database. DM8
migrations are therefore explicit, reviewed DIsql scripts under
`deploy/database/dm8`. Do not point the H2 Flyway location at DM8.

For a new empty DM8 schema:

1. Create a dedicated database user/schema with only the required privileges.
2. Start DIsql and connect without placing the password in shell history.
3. Run the scripts in ascending version order:

```text
start D:\path\to\deploy\database\dm8\V1__initial_schema.sql
start D:\path\to\deploy\database\dm8\V2__constraints_and_indexes.sql
start D:\path\to\deploy\database\dm8\V3__object_storage_metadata.sql
start D:\path\to\deploy\database\dm8\V4__report_object_storage_metadata.sql
start D:\path\to\deploy\database\dm8\V5__evidence_location_contract.sql
```

4. Verify the recorded version:

```sql
SELECT version_no, script_name, installed_at
FROM shipcad_schema_version
ORDER BY version_no;
```

5. Start the backend with the `prod` profile. JPA validates the schema and
fails startup if required tables or columns are missing.

The default production dialect is
`com.shipcad.review.database.ShipCadDmDialect`. It extends the official DM8
Hibernate 6.6 dialect but disables sequence discovery because all current
entity identifiers are assigned by the application. This avoids granting the
application user access to `SYS.SYSOBJECTS`. Revisit this boundary before
introducing database-generated sequence identifiers.

DM8 DDL may commit independently depending on server configuration. Back up
the schema before applying an upgrade, never rerun an already recorded script,
and inspect the last successful statement before recovering a failed upgrade.

The DM8 integration was certified on June 7, 2026 against DM8 Pack8 build
`03134284404-20250930-295335-20164`. V1/V2 completed without
SQL errors, Hibernate `validate` passed with a dedicated `RESOURCE` user, and
the DXF golden E2E suite passed 11/11 cases. This certifies compatibility, not
production load, backup, failover, or disaster-recovery readiness.

V3 adds object-storage metadata to `drawing_version`. It was applied on June 8,
2026 to the local DM8 development instance at `127.0.0.1:5237`; the version
record and new columns were verified, and the current backend passed Hibernate
`validate` and `/api/health` against that schema. Apply it before starting code
that includes the object-storage adapter.

V4 adds object-storage metadata to `report_document` so generated Markdown
reports can be stored as local or S3-compatible objects.

V5 adds version-scoped CAD handles to `parsed_entity` and the structured
`location_json` coordinate contract to `review_evidence`. V4 and V5 were
applied on June 8, 2026 to the isolated local DM8 development instance at
`127.0.0.1:5237`; their version records and columns were verified. The current
backend passed Hibernate schema validation and `/api/health` reported the
database, queue, and local object storage as healthy. The DXF golden E2E suite
also passed 11/11 cases against DM8 and a real CAD Worker. This remains a local
compatibility certification, not production load, backup, failover, or
disaster-recovery certification.

For the local Windows development instance installed at `D:\dm8task`, use:

```powershell
.\deploy\database\dm8\start-local-instance.ps1
.\deploy\database\dm8\stop-local-instance.ps1
```

These scripts contain no credentials and only manage the project port `5237`.
