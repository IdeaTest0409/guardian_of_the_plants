# Project Docs

Use these documents by purpose:

```text
development-status.md      Current milestone status, architecture, and next steps.
android-build.md           Build the Android APK.
android-migration-list.md  Historical Android migration notes.
local-api-smoke-test.md    Local Docker/nginx/server/db smoke test.
vps-smoke-test.md          Ubuntu VPS deployment smoke test.
vps-operations.md          VPS operations, updates, logs, and backup notes.
https-tls-plan.md          HTTPS/TLS plan for nginx on the VPS.
assistant-db-save-debug.md Historical DB save debugging notes.
db-migration.md           Current manual DB migration notes and future Flyway plan.
opencode-handoff.md        Broad project handoff and current status.
session-change-log.md      Session-level implementation history.
```

Current verified path:

```text
Android app
  -> VPS nginx
    -> Spring Boot server container
      -> PostgreSQL container
        -> app_logs
        -> chat_histories
        -> request_traces
      -> VoiceVOX Engine container
      -> external AI provider
  -> /admin/logs.html
```

Request flow tracing:

```text
Chat: received -> ai_call -> ai_response -> db_saved -> complete/error
TTS:  received -> voicevox_call -> voicevox_response -> complete/error
GET /api/logs/flow           recent flows
GET /api/logs/flow/{traceId} detailed steps
```

The next major operational tasks are HTTPS/TLS, admin viewer authentication,
rate limiting, and production-safe secret management.

CI:

```text
.github/workflows/android-build.yml  Android debug APK build check.
.github/workflows/server-build.yml   Server tests and Docker image build check.
```

Operational helper scripts:

```text
scripts/vps-update.sh     Pull, rebuild, restart, and check basic health on VPS.
scripts/vps-status.sh     Show compose status, health checks, and server logs.
scripts/secret-scan.ps1   Local scan for obvious committed secret patterns.
```
