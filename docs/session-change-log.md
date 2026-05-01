# Session Change Log

This file keeps a concise history of major implementation sessions. For the
current state and next steps, prefer `development-status.md`.

## 2026-05-01

### Log Download Fix

Fixed the browser log viewer download endpoint.

Symptom on VPS:

```text
GET /api/logs/download?hours=1&type=all returned HTTP 500.
The browser showed: Download failed: Download failed
```

Root cause:

```text
PostgreSQL rejected timestamp comparison:
timestamp with time zone >= character varying
```

The controller generated the `since` value as a formatted `String`, then the
repository passed it to SQL predicates against `TIMESTAMPTZ` columns:

```sql
WHERE created_at >= ?
WHERE received_at >= ?
```

Fix:

```text
LogViewerController now creates an OffsetDateTime.
LogViewerRepository now accepts OffsetDateTime for getChatLogsSince/getAppLogsSince.
The browser download handler now includes HTTP status and response body preview on failure.
```

Changed:

```text
server/src/main/java/com/example/guardianplants/controller/LogViewerController.java
server/src/main/java/com/example/guardianplants/LogViewerRepository.java
server/src/main/resources/static/admin/logs.html
```

### Request Flow Tracing

Added request tracing for chat and TTS requests.

Created:

```text
server/src/main/java/com/example/guardianplants/RequestTraceRepository.java
server/src/main/java/com/example/guardianplants/service/RequestTraceService.java
db/init/002_request_traces.sql
```

Endpoints:

```text
GET /api/logs/flow
GET /api/logs/flow/{traceId}
```

Tracked steps:

```text
Chat: received -> ai_call -> ai_response -> db_saved -> complete/error
TTS:  received -> voicevox_call -> voicevox_response -> complete/error
```

Follow-up in this session:

```text
Request trace retention guard added:
- prune every 200 inserted steps
- keep at most 10,000 rows
- delete rows older than 7 days
```

### Android TTS Fixes

Improved server VoiceVOX playback reliability.

Changed:

```text
android/app/src/main/java/com/example/smartphonapptest001/data/network/ServerTtsApi.kt
android/app/src/main/java/com/example/smartphonapptest001/ui/SmartphoneChatApp.kt
```

Notes:

```text
Read timeout increased for long TTS generation.
MediaPlayer cleanup releases resources and deletes temp WAV files on completion, error, and exceptions.
```

### Documentation Cleanup

Rewrote current documentation to remove mojibake and make the active state
clear.

Created:

```text
docs/https-tls-plan.md
docs/vps-operations.md
```

Updated:

```text
docs/README.md
docs/development-status.md
```

## 2026-04-30

### Server-Side Chat API

Added server-routed chat through `ProviderType.SERVER`.

Flow:

```text
Android -> nginx -> Spring Boot /api/chat -> external AI provider
```

Chat history is stored in PostgreSQL `chat_histories`.

### Server-Side VoiceVOX TTS

Added server endpoints:

```text
POST /api/tts/synthesize
GET  /api/tts/speakers
GET  /api/tts/health
```

VoiceVOX runs through Docker Compose and is controlled by:

```text
VOICEVOX_ENABLED
VOICEVOX_BASE_URL
```

### Browser Log Viewer

Added:

```text
/admin/logs.html
```

Backing endpoints:

```text
GET /api/logs/chat
GET /api/logs/app
GET /api/logs/health
GET /api/logs/download
```

Current risk:

```text
The admin viewer is not authenticated yet.
```

### VPS Docker Compose

Spring Boot now runs inside Docker Compose.

Current public exposure:

```text
external network -> nginx only
nginx -> server over Docker network
server -> db over Docker network
server -> voicevox over Docker network
```

## Resolved Historical Issues

Earlier notes mentioned assistant messages not being saved to DB. Current
milestone documentation states that both user and assistant messages are stored
in `chat_histories`. If this regresses, use the request flow viewer and server
logs to identify the failing step.

## Open Operational Items

```text
HTTPS/TLS for nginx
Authentication for /admin/logs.html
Rate limiting for /api/chat and /api/tts/synthesize
Proper DB migration workflow for existing volumes
Production-safe secret handling in VPS .env
```
