# Database Migration Notes

Current state:

```text
db/init/001_init.sql
db/init/002_request_traces.sql
```

These files are mounted into `/docker-entrypoint-initdb.d` and run only when
PostgreSQL creates a fresh volume. They do not automatically run against an
existing VPS database.

## Current Manual Approach

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

## Recommended Future Tooling

Before adding more schema changes, introduce one migration tool:

```text
Flyway
```

Recommended layout after that change:

```text
server/src/main/resources/db/migration/V1__init.sql
server/src/main/resources/db/migration/V2__request_traces.sql
```

Do not mix automatic Flyway migrations with ad hoc edits to the same production
schema without documenting which version has been applied.
