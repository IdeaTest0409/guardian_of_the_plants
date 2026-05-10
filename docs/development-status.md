# Development Status: guardian_of_the_plants

Last updated: 2026-05-11

## Current Summary

The project baseline is:

```text
Android app (Jetpack Compose)
  -> VPS nginx (HTTP today, HTTPS planned)
    -> Spring Boot server container
      -> PostgreSQL container
      -> VoiceVOX Engine container
      -> external AI provider or AP server host Ollama
  -> Browser admin viewer at /admin/logs.html
  -> Browser AI settings at /admin/ai.html
  -> Browser live stage at /live/stage.html
```

Android defaults to the `SERVER` provider. The VPS server owns AI provider
configuration, so Android does not need an AI API key for server-routed chat.
When Android AP server control mode is enabled, Android sends only current text
and selected images while the AP server owns guardian prompts and live behavior.

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

`GET /api/logs/download` exports chat history, app logs, and request flows for
the selected time window. The timestamp filter uses `OffsetDateTime` so
PostgreSQL `TIMESTAMPTZ` comparisons work correctly.

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
Android startup reporting can be disabled at build time
Server-side chat and TTS input validation
Server-side per-IP rate limiting for app-start, chat, and TTS
Chat validation supports large image data URL messages up to 300,000 characters
Android logs server-side SSE error payloads instead of treating them as empty replies
Flyway migrations for server-managed schema
Retention pruning for app_logs and chat_histories
Server controller tests for health, app-start, and TTS
GitHub Actions for Android debug build and server test/Docker build
VPS helper scripts
Local secret scan script
```

### Milestone 7: Live Stage Foundation and AI Switching

Implemented:

```text
POST /api/live/message accepts live messages.
GET /api/live/state exposes latest live state for the browser stage.
/live/stage.html provides the first OBS/browser-source stage.
nginx proxies /live/ to the Spring Boot server.
/admin/ai.html switches the server-side active AI profile.
GET /api/ai/profiles lists configured profiles with API keys masked.
POST /api/ai/active changes the active profile.
POST /api/ai/test verifies provider reachability.
server_settings stores server-managed settings through Flyway migration V3.
docker-compose.yml lets the server container reach AP host Ollama through host.docker.internal.
Android AP server control mode avoids sending local guardian prompts to the AP server.
```

Current AP server AI options:

```text
Cloud default:          https://ollama.com/v1, model gemma4:31b-cloud
AP Ollama gemma4:e2b:  http://host.docker.internal:11434/v1, model gemma4:e2b
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
server_settings
```

Initial SQL:

```text
db/init/001_init.sql
db/init/002_request_traces.sql
```

SQL files in `db/init` only run when PostgreSQL initializes a new volume. The
Spring Boot server now also has Flyway migrations under
`server/src/main/resources/db/migration` for ongoing schema management.

## Configuration

Server-side `.env`:

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
AI_BASE_URL
AI_API_KEY
AI_MODEL
AI_ACTIVE_PROFILE
AI_PROFILES_JSON
VOICEVOX_ENABLED
VOICEVOX_BASE_URL
RATE_LIMIT_*
RETENTION_*
FLYWAY_ENABLED
```

Android local build configuration:

```text
android/local.properties
guardian.api.baseUrl=http://<SERVER_HOST>/api
guardian.appStartReporting.enabled=true
```

`android/local.properties` is intentionally ignored by Git. If the URL is
blank, malformed, or does not end with `/api`, Android skips startup reporting
and writes a local warning log.

## Known Risks

| Risk | Status | Mitigation |
|------|--------|------------|
| VPS still uses HTTP | Open | Add HTTPS/TLS through nginx |
| `/admin/logs.html` has no auth | Open | Add Basic Auth or token gate |
| Rate limiting is basic | Partially handled | Spring per-IP minute limits added; tune or move to nginx later |
| TTS CPU load | Expected | Keep `VOICEVOX_ENABLED=false` when not needed |
| Request trace growth | Partially handled | Retention guard added; consider scheduled cleanup |
| DB migration is new | Partially handled | Flyway added; verify carefully on VPS existing volumes |
| Android Filament native crashes | Known | 3D expression off by default |
| Secrets in `.env` | Manual | Never commit `.env`; keep AI keys server-side |
| Android `CLOUD` provider | Legacy | Kept for direct LM Studio testing; `SERVER` remains recommended |
| Live stage can show internal prompt/object text | Mitigated | Live state now extracts display text only and replaces internal auto-talk prompts |
| Admin AI/log pages have no auth | Open | Add Basic Auth or token gate before wider exposure |

## Next Steps

1. Add server-generated TTS/audio playback to the live stage.
2. Add `/admin/live.html` for live ON/OFF, auto-talk, image preview, and manual speak controls.
3. Add authentication for `/admin/logs.html`, `/admin/ai.html`, and future admin pages.
4. Split AI profiles by purpose: chat, image diagnostic, live talk, summary, and safety.
5. Add HTTPS/TLS to nginx on the VPS.
6. Move RAG/knowledge management server-side.
