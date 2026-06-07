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
```

4. Verify the recorded version:

```sql
SELECT version_no, script_name, installed_at
FROM shipcad_schema_version
ORDER BY version_no;
```

5. Start the backend with the `prod` profile. JPA validates the schema and
fails startup if required tables or columns are missing.

DM8 DDL may commit independently depending on server configuration. Back up
the schema before applying an upgrade, never rerun an already recorded script,
and inspect the last successful statement before recovering a failed upgrade.

The repository can validate H2 migrations automatically. DM8 scripts require
an actual DM8 test instance before they can be marked production-certified.
