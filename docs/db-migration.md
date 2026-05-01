# Database Migration Notes

Current state:

```text
db/init/001_init.sql
db/init/002_request_traces.sql
```

These files are mounted into `/docker-entrypoint-initdb.d` and run only when
PostgreSQL creates a fresh volume. They do not automatically run against an
existing VPS database.

## Current Implementation

Spring Boot includes Flyway and runs migrations from:

```text
server/src/main/resources/db/migration
```

Current files:

```text
V1__init.sql
V2__request_traces.sql
```

`baseline-on-migrate` is enabled so an existing VPS database can be brought
under Flyway management without dropping current tables.

## Manual Approach For Existing `db/init` Files

For a fresh reset:

```bash
docker compose down -v
docker compose up -d db
```

For an existing database, review the SQL first, then apply only the missing
schema manually:

```bash
docker exec -i guardian-postgres psql -U guardian_user -d guardian_plants < db/init/002_request_traces.sql
```

If the table already exists, the current SQL uses `create table if not exists`
and is safe to re-run for the known request trace table.

Do not mix automatic Flyway migrations with ad hoc edits to the same production
schema without documenting which version has been applied.
