# Session Change Log

This file keeps a concise history of major implementation sessions. For the
current state and next steps, prefer `development-status.md`.

## 2026-05-01

### Android Approved Plant Image Reuse

Added a chat-screen switch to skip repeated hidden image diagnostics after the
current plant image has already been accepted by the diagnostic AI.

Behavior:

```text
Default is off.
Selecting or capturing a new plant image turns reuse off.
When hidden image diagnostic returns guardianTargetPresent=true, reuse turns on.
While reuse is on for the same image, Android skips the hidden diagnostic AI call.
The selected image is still included in the main assistant request.
Users can turn reuse off manually to force a fresh diagnostic.
```

Changed:

```text
android/app/src/main/java/com/example/smartphonapptest001/viewmodel/ChatViewModel.kt
android/app/src/main/java/com/example/smartphonapptest001/ui/screen/ChatScreen.kt
android/app/src/main/java/com/example/smartphonapptest001/ui/SmartphoneChatApp.kt
android/app/src/main/java/com/example/smartphonapptest001/MainActivity.kt
```

### Server VoiceVOX WAV Buffer Fix

Fixed a case where Android received an AI reply but did not play speech.

Observed symptoms:

```text
Android logs:
Assistant reply received
TTS synthesis failed status=500
VoiceVox returned empty audio

Request flow:
VoiceVOX synthesis failed: 200 OK from POST http://voicevox:50021/synthesis,
but response failed with DataBufferLimitException: Exceeded limit on max bytes
to buffer : 262144
```

Root cause:

```text
VoiceVOX returned a WAV response larger than Spring WebClient's default 256KB
in-memory buffer. The server failed before it could return audio to Android.
```

Fix:

```text
Server WebClient maxInMemorySize increased to 10MB.
TTS error responses now return JSON explicitly.
TTS request tracing records synthesis failures as voicevox_synthesis.
```

Changed:

```text
server/src/main/java/com/example/guardianplants/config/WebClientConfig.java
server/src/main/java/com/example/guardianplants/controller/TtsController.java
server/src/main/java/com/example/guardianplants/service/TtsService.java
server/src/test/java/com/example/guardianplants/controller/TtsControllerTest.java
```

### Server Chat Validation And Android Error Visibility

Fixed a case where the Android app appeared to receive no response from the
server even though nginx showed successful `POST /api/chat` requests.

Observed symptoms:

```text
Android logs:
Server streaming completed
totalChars=0
replyChars=0

nginx access log:
POST /api/chat HTTP/1.1" 200 77
POST /api/chat HTTP/1.1" 200 82
```

The small 77/82 byte responses indicated that Spring Boot was returning a short
SSE error payload, not a real AI response.

Likely trigger:

```text
Android realtime images are sent as base64 data URLs.
imageDataUrlChars was around 90,000 to 105,000 characters.
The server chat validation limit was 8,000 characters.
```

Fix:

```text
MAX_CHAT_MESSAGE_CHARS increased to 300,000.
Chat validation failures are now logged by Spring Boot.
Chat validation failures are recorded in request_traces.
Android ServerChatApi now detects {"error":"..."} SSE payloads and logs them as errors instead of silently returning an empty reply.
```

Changed:

```text
server/src/main/java/com/example/guardianplants/ApiValidation.java
server/src/main/java/com/example/guardianplants/controller/ChatController.java
android/app/src/main/java/com/example/smartphonapptest001/data/network/ServerChatApi.kt
```

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
