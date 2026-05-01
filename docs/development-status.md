# Development Status: guardian_of_the_plants

Last updated: 2026-05-01

## Current Summary

The project has moved beyond the initial app-start logging milestone. The
current development baseline is:

```text
Android app (Jetpack Compose)
  -> VPS nginx (HTTP today, HTTPS planned)
    -> Spring Boot server container
      -> PostgreSQL container
      -> VoiceVOX Engine container
      -> external AI provider
  -> Browser admin viewer at /admin/logs.html
```

Android defaults to the `SERVER` provider. The VPS server owns the AI provider
configuration so Android no longer needs an AI API key for server-routed chat.

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

Important files:

```text
server/src/main/java/com/example/guardianplants/controller/ChatController.java
server/src/main/java/com/example/guardianplants/service/ChatService.java
server/src/main/java/com/example/guardianplants/service/ProviderResolver.java
server/src/main/java/com/example/guardianplants/dto/ChatRequest.java
android/app/src/main/java/com/example/smartphonapptest001/data/network/ServerChatApi.kt
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
```

The page shows chat history, app logs, health status, and request flow data. It
currently has no authentication.

### Milestone 5: Android VoiceVOX Integration

Android settings include a server VoiceVOX toggle only for `SERVER` provider.
When enabled, assistant replies are synthesized by the VPS through
`POST /api/tts/synthesize`. If disabled, Android TextToSpeech is used.

### Milestone 6: Request Flow Tracing

Every chat and TTS request receives a `trace_id`.

Pipeline steps:

```text
Chat: received -> ai_call -> ai_response -> db_saved -> complete/error
TTS:  received -> voicevox_call -> voicevox_response -> complete/error
```

Endpoints:

```text
GET /api/logs/flow
GET /api/logs/flow/{traceId}
```

Storage:

```text
request_traces
```

Current retention guard:

```text
prune every 200 inserted steps
keep at most 10,000 rows
delete rows older than 7 days
```

### Milestone 7: Android TTS Fixes

`ServerTtsApi` timeout handling was extended for long VoiceVOX generation.
MediaPlayer cleanup now releases resources and deletes temporary WAV files on
completion, error, and prepare/start exceptions.

## Current Docker Services

```text
nginx     externally published on NGINX_HTTP_PORT, default 80
server    internal only
db        internal only
voicevox  internal only
```

Only nginx should be exposed to the network.

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

Note: SQL files in `db/init` only run when PostgreSQL initializes a new volume.
For an existing VPS volume, apply schema changes manually or introduce a real
migration tool later.

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

`android/local.properties` is intentionally ignored by Git.

## Known Risks

| Risk | Status | Mitigation |
|------|--------|------------|
| VPS still uses HTTP | Open | Add HTTPS/TLS through nginx |
| `/admin/logs.html` has no auth | Open | Add Basic Auth or token gate |
| TTS CPU load | Expected | Keep `VOICEVOX_ENABLED=false` when not needed; add rate limiting |
| Request trace growth | Partially handled | Retention guard added; consider scheduled cleanup |
| `db/init` is not migration tooling | Open | Add Flyway/Liquibase or manual migration docs |
| Android Filament native crashes | Known | 3D expression off by default |
| Secrets in `.env` | Manual | Never commit `.env`; keep AI keys server-side |

## Next Steps

1. Add HTTPS/TLS to nginx on the VPS.
2. Add authentication for `/admin/logs.html`.
3. Add rate limiting for `/api/chat` and `/api/tts/synthesize`.
4. Decide whether to keep `CLOUD` provider visible in Android or mark it legacy.
5. Add a proper DB migration workflow.
6. Consider async TTS jobs if VoiceVOX latency becomes a problem.
7. Move RAG/knowledge management server-side.
