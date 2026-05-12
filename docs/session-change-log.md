# Session Change Log

This file keeps a concise history of major implementation sessions. For the
current state and next steps, prefer `development-status.md`.

## 2026-05-01

### Server AI Profile Selection And Android Server-Managed Prompt Mode

Added browser-managed AI profile switching for the AP server and a smartphone
setting for sending only the user text and plant image to the server.

Behavior:

```text
/admin/ai.html lists configured AI profiles, runs connectivity tests, and switches the active profile.
GET /api/ai/profiles returns the active profile and configured profiles without exposing API keys.
POST /api/ai/active stores the selected profile in server_settings.
POST /api/ai/test calls the profile's /models endpoint for a quick connectivity check.
ProviderResolver now uses the active profile while preserving the old AI_BASE_URL/AI_MODEL fallback.
Android Settings now shows "APサーバー制御モード" near the top.
When enabled in SERVER mode, Android sends only the current user text and plant image instead of local guardian prompts/history.
The live message endpoint adds a minimal server-side guardian prompt when the client does not send one.
```

Changed:

```text
server/src/main/java/com/example/guardianplants/service/ProviderResolver.java
server/src/main/java/com/example/guardianplants/ServerSettingsRepository.java
server/src/main/java/com/example/guardianplants/controller/AiProfileController.java
server/src/main/java/com/example/guardianplants/controller/LiveController.java
server/src/main/java/com/example/guardianplants/dto/AiProfile.java
server/src/main/resources/db/migration/V3__server_settings.sql
server/src/main/resources/static/admin/ai.html
android/app/src/main/java/com/example/smartphonapptest001/data/model/AppSettings.kt
android/app/src/main/java/com/example/smartphonapptest001/viewmodel/ChatViewModel.kt
```

### Live Stage Foundation

Added the first server-led AItuber foundation while keeping the existing Android
chat path compatible.

Behavior:

```text
Android non-streaming server chat tries POST /api/live/message first.
If /api/live/message is unavailable, Android falls back to the existing /api/chat stream path.
POST /api/live/message generates the guardian reply on the server and updates Live State.
GET /api/live/state exposes the latest user text, assistant text, image data URL, status, and future audio URL.
/live/stage.html polls Live State and renders an OBS-friendly browser stage.
The initial live response is text-first; audioUrl/audioFormat fields are reserved for the next phase.
```

Gitea:

```text
Added local git remote named gitea.
Configured URL: http://158.220.99.197:3000/tomoki/guardian_of_the_plants.git
No push was performed.
git ls-remote currently returns Not found, so the Gitea repository path still needs confirmation.
```

Changed:

```text
server/src/main/java/com/example/guardianplants/service/ChatService.java
server/src/main/java/com/example/guardianplants/service/LiveStateService.java
server/src/main/java/com/example/guardianplants/controller/LiveController.java
server/src/main/java/com/example/guardianplants/dto/LiveMessageResponse.java
server/src/main/resources/static/live/stage.html
android/app/src/main/java/com/example/smartphonapptest001/data/network/ServerChatApi.kt
android/app/src/main/java/com/example/smartphonapptest001/data/network/ServerChatRequest.kt
```

Follow-up:

```text
nginx now proxies /live/ to the Spring Boot server so /live/stage.html is served instead of the nginx fallback text.
docker-compose now maps host.docker.internal to the Docker host gateway so the server container can reach host Ollama.
```

### Browser Log Viewer Delete Action

Added a server-side delete action for the browser log viewer.

Behavior:

```text
/admin/logs.html shows a "ログを全て削除する" button.
The browser asks for confirmation before deleting.
DELETE /api/logs deletes the displayed log data from request_traces, chat_histories, and app_logs.
The page reloads health, flow, chat, and app log panels after deletion.
```

Changed:

```text
server/src/main/java/com/example/guardianplants/LogViewerRepository.java
server/src/main/java/com/example/guardianplants/controller/LogViewerController.java
server/src/main/resources/static/admin/logs.html
```

### Android Chat UI And Voice Playback Reliability

Improved the Android chat screen for small phone heights and made VoiceVOX
playback recover when AAC is downloaded but the device cannot start playback.

Behavior:

```text
Quick reply buttons now stay in one horizontal scroll row.
The approved-image reuse switch uses less vertical space.
VoiceVOX playback tries AAC first.
If Android MediaPlayer cannot start AAC playback, the app requests WAV and retries once.
Log View exposes a Japanese "ログを全て削除する" button for clearing local logs.
```

Changed:

```text
android/app/src/main/java/com/example/smartphonapptest001/ui/screen/ChatScreen.kt
android/app/src/main/java/com/example/smartphonapptest001/ui/SmartphoneChatApp.kt
android/app/src/main/java/com/example/smartphonapptest001/ui/screen/LogsScreen.kt
```

### Server VoiceVOX AAC Output Option

Added optional AAC/M4A output for server-routed VoiceVOX TTS while keeping WAV
as the compatible fallback path.

Behavior:

```text
Android requests format=aac from POST /api/tts/synthesize.
The server still asks VoiceVOX for WAV.
When AAC is requested, the server converts WAV to M4A/AAC with ffmpeg.
Android stores the response as .m4a for MediaPlayer playback.
If AAC synthesis fails on Android, it retries once with format=wav.
```

Tracing:

```text
voicevox_response records WAV bytes and VoiceVOX duration.
audio_encode records AAC bytes and encode duration.
```

Changed:

```text
server/src/main/java/com/example/guardianplants/dto/TtsRequest.java
server/src/main/java/com/example/guardianplants/ApiValidation.java
server/src/main/java/com/example/guardianplants/service/TtsService.java
server/src/main/java/com/example/guardianplants/service/RequestTraceService.java
server/src/main/java/com/example/guardianplants/controller/TtsController.java
server/src/test/java/com/example/guardianplants/controller/TtsControllerTest.java
server/Dockerfile
android/app/src/main/java/com/example/smartphonapptest001/data/network/ServerTtsApi.kt
android/app/src/main/java/com/example/smartphonapptest001/ui/SmartphoneChatApp.kt
```

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

## 2026-05-11

### Live Stage Display Safety and OBS Layout

Updated the first live stage implementation to make it safer for OBS/browser
output.

Changed behavior:

```text
LiveStateService extracts display text from structured message content instead
of using contentAsString().
Internal auto-talk/system instruction text is not exposed as stage user text.
LiveController ignores client-provided system prompts for live messages and
injects the AP-server-managed guardian prompt.
LiveController preserves image_url parts while replacing internal user text
with a short live-talk request.
/live/stage.html was rebuilt as a 16:9 OBS-friendly layout with Japanese
placeholders, safe caption areas, plant image framing, and guardian status.
```

Changed:

```text
server/src/main/java/com/example/guardianplants/service/LiveStateService.java
server/src/main/java/com/example/guardianplants/controller/LiveController.java
server/src/main/resources/static/live/stage.html
```

### Live Stage Audio, Temporary 3D Guardian, and Live Control Page

Added the next live-stage iteration.

Behavior:

```text
/live/stage.html uses smaller three-line caption text.
/live/stage.html renders a temporary Three.js guardian with status-based motion.
/live/stage.html has an Enable Audio button and plays new live audio URLs.
POST /api/live/message now attempts server-side VoiceVOX synthesis for the
assistant reply, encodes AAC/M4A, stores recent audio in memory, and exposes
audioUrl in live state.
GET /api/live/audio/{id} returns recent live audio.
/admin/live.html can send manual live messages, refresh live state, and preview
the stage.
```

Changed:

```text
server/src/main/java/com/example/guardianplants/controller/LiveController.java
server/src/main/java/com/example/guardianplants/service/LiveAudioService.java
server/src/main/java/com/example/guardianplants/service/LiveStateService.java
server/src/main/resources/static/live/stage.html
server/src/main/resources/static/admin/live.html
```

### Live Stage Android GLB and PC Voice Input

Changed the live stage character from the temporary Three.js primitive guardian
to the Android angel model.

Behavior:

```text
angel_egna.glb is copied into server static assets at /live/assets/models/angel_egna.glb.
/live/stage.html loads angel_egna.glb with Three.js GLTFLoader.
The stage keeps the existing Enable Audio flow and attempts simple mouth morph
movement while audio is playing if matching morph targets are present.
/admin/live.html supports PC text chat, Web Speech API voice input, and quick
choice buttons for common plant questions.
/admin/live.html can attach a local plant image file to the next chat message;
the file is converted to a data URL in the browser and sent as an image part.
/admin/live.html has an Auto topic toggle. While the page is open, it can send
a periodic guardian-initiated prompt.
/admin/live.html has a 3D Pose selector. The selected preset is stored through
GET/POST /api/live/settings and applied by /live/stage.html.
The live preview iframe is reloaded with a cache-busting URL after pose changes
so `/admin/live.html` reflects the same pose as direct `/live/stage.html`.
AI profile selection and connection testing are now available directly in
/admin/live.html. The built-in Ollama Cloud profile list includes
`deepseek-v4-flash` using the same Ollama API key source as the other cloud
profiles.
The first pass did not visibly change the model posture because GLB animation
or bone-axis assumptions could override the rest pose. The stage now disables
the mixer for non-raw presets, applies stronger arm-bone rotations, and logs
`poseBoneCount` when a preset is applied.
Browser-side 3D initialization and GLB load failures are posted to
POST /api/logs and show up in /admin/logs.html as category LiveStage3D.
The App Logs table now includes a short details preview for client-side error
context such as browser message, user agent, and model URL.
/live/stage.html now normalizes the GLB to the ground, plays an included idle
animation when available, lowers arm bones as a fallback, and uses audio
waveform level for mouth morphs instead of a fixed timer.
```

Changed:

```text
server/src/main/resources/static/live/assets/models/angel_egna.glb
server/src/main/resources/static/live/stage.html
server/src/main/resources/static/admin/live.html
server/src/main/java/com/example/guardianplants/AppLogRequest.java
server/src/main/java/com/example/guardianplants/AppStartController.java
server/src/main/java/com/example/guardianplants/LogViewerRepository.java
server/src/main/resources/static/admin/logs.html
server/src/test/java/com/example/guardianplants/AppStartControllerTest.java
```

### Live AItuber Strategy and Handoff Docs

Added a handoff memo for the live/AItuber direction:

```text
docs/live-aituber-roadmap.md
```

Captured:

```text
AP server and Gitea server addresses
Gitea SSH remote and AP server update commands
current live stage/admin URLs
AI profile switching model
AP server host Ollama gemma4:e2b setup
Android AP server control mode behavior
known live stage prompt-display issue
recommended roadmap for OBS stage, audio, guardian visuals, live controls, and admin security
```

Also refreshed:

```text
docs/README.md
docs/development-status.md
```

### Admin Security and Live Control Layout

Added optional Basic Auth for admin surfaces and sensitive live/AI write APIs.

Behavior:

```text
Set ADMIN_AUTH_PASSWORD in .env to enable Basic Auth.
ADMIN_AUTH_USERNAME defaults to admin.
/admin/* is protected.
/api/ai/* is protected.
POST /api/live/message, /api/live/settings, and /api/live/plant-image are protected.
DELETE /api/logs is protected.
The public live stage, live state reads, live audio, and Android app log POSTs remain available.
```

Also reorganized `/admin/live.html` into clearer sections:

```text
Message
Plant Image
Automation
Stage
AI
```
