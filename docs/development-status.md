# Development Status: guardian_of_the_plants

Last updated: 2026-05-01

## Current Summary

The project baseline is:

```text
Android app (Jetpack Compose)
  -> VPS nginx (HTTP today, HTTPS planned)
    -> Spring Boot server container
      -> PostgreSQL container
      -> VoiceVOX Engine container
      -> external AI provider
  -> Browser admin viewer at /admin/logs.html
```

Android defaults to the `SERVER` provider. The VPS server owns AI provider
configuration, so Android does not need an AI API key for server-routed chat.

## Completed Milestones

### Milestone 1: App Start Logging

```text
Android app start -> nginx -> Spring Boot -> PostgreSQL app_logs
```

Verified with a physical Xiaomi device.

### Milestone 2: Server-Side Chat API

```text
Android ProviderType.SERVER sends chat messages to POST /api/chat.
Spring Boot forwards the request to the configured AI provider.
SSE streams back to Android.
chat_histories stores user and assistant messages.
AI provider configuration lives in server .env.
```

### Milestone 3: Server-Side VoiceVOX TTS

```text
POST /api/tts/synthesize returns WAV audio.
GET /api/tts/speakers returns VoiceVOX speaker data.
VoiceVOX Engine runs through Docker Compose.
VOICEVOX_ENABLED controls server-side availability.
```

Android can use server VoiceVOX TTS when ProviderType.SERVER is selected.

Current Android speaker choices:

```text
四国めたん    speaker=2
ずんだもん    speaker=3
春日部つむぎ  speaker=8
```

### Milestone 4: Browser Log Viewer

The VPS serves a dashboard at:

```text
/admin/logs.html
```

Current capabilities:

```text
GET /api/logs/chat
GET /api/logs/app
GET /api/logs/health
GET /api/logs/download
GET /api/logs/flow
GET /api/logs/flow/{traceId}
```

The page has no authentication yet and must be protected before public use.

### Milestone 5: Request Flow Tracing

Every chat and TTS request receives a `trace_id`.

```text
Chat: received -> ai_call -> ai_response -> db_saved -> complete/error
TTS:  received -> voicevox_call -> voicevox_response -> complete/error
```

Storage:

```text
request_traces
```

Retention guard:

```text
prune every 200 inserted steps
keep at most 10,000 rows
delete rows older than 7 days
```

### Milestone 6: Operational Hardening

Implemented:

```text
Compose healthchecks for nginx, server, and VoiceVOX
Only nginx has a host ports mapping
Android startup reporter validates guardian.api.baseUrl
Server-side chat and TTS input validation
Server controller tests for health, app-start, and TTS
GitHub Actions for Android debug build and server test/Docker build
VPS helper scripts
Local secret scan script
```

## Current Docker Services

```text
nginx     externally published on NGINX_HTTP_PORT, default 80
server    no host port; reached by nginx over Compose network
db        no host port; reached by server over Compose network
voicevox  no host port; reached by server over Compose network
```

Only nginx should be published with a host `ports:` mapping. The server must
still be able to reach the external AI provider over outbound HTTPS.

## Current Database Tables

```text
app_logs
chat_histories
request_traces
```

Initial SQL:

```text
db/init/001_init.sql
db/init/002_request_traces.sql
```

SQL files in `db/init` only run when PostgreSQL initializes a new volume. For
an existing VPS volume, apply schema changes manually or introduce a migration
tool.

## Configuration

Server-side `.env`:

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
AI_BASE_URL
AI_API_KEY
AI_MODEL
VOICEVOX_ENABLED
VOICEVOX_BASE_URL
```

Android local build configuration:

```text
android/local.properties
guardian.api.baseUrl=http://<SERVER_HOST>/api
```

`android/local.properties` is intentionally ignored by Git. If the URL is
blank, malformed, or does not end with `/api`, Android skips startup reporting
and writes a local warning log.

## Known Risks

| Risk | Status | Mitigation |
|------|--------|------------|
| VPS still uses HTTP | Open | Add HTTPS/TLS through nginx |
| `/admin/logs.html` has no auth | Open | Add Basic Auth or token gate |
| Chat/TTS rate limiting is missing | Open | Add nginx or Spring rate limiting |
| TTS CPU load | Expected | Keep `VOICEVOX_ENABLED=false` when not needed |
| Request trace growth | Partially handled | Retention guard added; consider scheduled cleanup |
| `db/init` is not migration tooling | Open | Add Flyway/Liquibase or manual migration docs |
| Android Filament native crashes | Known | 3D expression off by default |
| Secrets in `.env` | Manual | Never commit `.env`; keep AI keys server-side |
| Android `CLOUD` provider | Legacy | Kept for direct LM Studio testing; `SERVER` remains recommended |

## Next Steps

1. Add HTTPS/TLS to nginx on the VPS.
2. Add authentication for `/admin/logs.html`.
3. Add rate limiting for `/api/chat` and `/api/tts/synthesize`.
4. Decide whether to keep `CLOUD` provider visible in Android or mark it legacy.
5. Add a proper DB migration workflow.
6. Consider async TTS jobs if VoiceVOX latency becomes a problem.
7. Move RAG/knowledge management server-side.
