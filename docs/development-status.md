# Development Status: guardian_of_the_plants

Last updated: 2026-05-01 (Request Flow Tracing + TTS Android Fix)

## Development Tools

This project is actively developed using **OpenCode** ([opencode.ai](https://opencode.ai)), an AI-powered CLI coding assistant. All milestones from Milestone 2 onward were implemented with opencode's assistance.

## Completed Milestones

### Milestone 1: App Start Logging (Done)

```text
Android app sends one log entry.
Spring Boot receives it.
PostgreSQL app_logs stores it.
The Android app still works even if the server is unavailable.
```

### Milestone 2: Server-Side Chat API (Done - with opencode)

```text
Android app sends chat messages to /api/chat via SSE.
Spring Boot proxies to AI provider (Ollama Cloud / configured provider).
SSE stream flows: AI provider -> server -> Android.
PostgreSQL chat_histories stores user + assistant messages.
No AI API keys on the Android device.
Provider selection is server-side (.env).
Android ProviderType.SERVER routes through VPS.
Existing LOCAL / CLOUD / OLLAMA_CLOUD providers remain functional.
```

Key files:
- `server/src/main/java/com/example/guardianplants/controller/ChatController.java`
- `server/src/main/java/com/example/guardianplants/service/ChatService.java`
- `server/src/main/java/com/example/guardianplants/service/ProviderResolver.java`
- `server/src/main/java/com/example/guardianplants/dto/ChatRequest.java`

### Milestone 3: Server-Side VoiceVOX TTS (Done - with opencode)

```text
Server exposes POST /api/tts/synthesize for VoiceVOX text-to-speech.
POST /api/tts/synthesize accepts { text, speaker } and returns WAV audio.
GET /api/tts/speakers lists available VoiceVOX speakers.
VoiceVOX Engine runs as a Docker container (default start, no profile needed).
VOICEVOX_ENABLED in .env controls TTS availability.
nginx /api/ proxy covers both /api/chat and /api/tts.
```

Key files:
- `server/src/main/java/com/example/guardianplants/controller/TtsController.java`
- `server/src/main/java/com/example/guardianplants/service/TtsService.java`
- `server/src/main/java/com/example/guardianplants/service/TtsHealthService.java`
- `server/src/main/java/com/example/guardianplants/config/VoiceVoxConfig.java`

### Milestone 4: Browser Log Viewer (Done - with opencode)

```text
GET /api/logs/chat returns recent chat history from PostgreSQL.
GET /api/logs/app returns recent app logs from PostgreSQL.
GET /api/logs/health returns server/DB status, log counts, error counts, VoiceVOX health.
/admin/logs.html serves a browser UI with health status bar, Chat History table, and App Logs table.
Each section has a Refresh button. Auto-refresh every 30 seconds.
ERROR rows highlighted in red. WARN/INFO severity color-coded.
Mobile-friendly responsive design. No authentication required (dashboard mode).
```

Key files:
- `server/src/main/java/com/example/guardianplants/controller/LogViewerController.java`
- `server/src/main/java/com/example/guardianplants/LogViewerRepository.java`
- `server/src/main/resources/static/admin/logs.html`

### Milestone 5: Android VoiceVox Integration (Done - with opencode)

```text
Android app offers toggle between device TextToSpeech and server VoiceVox TTS.
VoiceVox toggle visible only when ProviderType.SERVER is selected.
3 selectable VoiceVox speakers: 四国めたん (2), ずんだもん (3), 春日部つむぎ (8).
Settings saved via DataStore (voicevox_enabled, voicevox_speaker).
GuardianReplySpeechEffect routes to ServerTtsApi when VoiceVox is enabled.
ServerTtsApi downloads WAV via POST /api/tts/synthesize, plays with MediaPlayer.
VoiceVox disabled → falls back to Android TextToSpeech.
Build compiles cleanly (warnings only, no errors).
```

Key files created:
- `android/app/src/main/java/com/example/smartphonapptest001/data/model/VoiceVoxSpeaker.kt`
- `android/app/src/main/java/com/example/smartphonapptest001/data/network/ServerTtsApi.kt`

Key files modified:
- `android/app/src/main/java/com/example/smartphonapptest001/data/model/AppSettings.kt` — added `voiceVoxEnabled`, `voiceVoxSpeaker`
- `android/app/src/main/java/com/example/smartphonapptest001/data/SettingsRepository.kt` — persistence keys + parser
- `android/app/src/main/java/com/example/smartphonapptest001/viewmodel/SettingsViewModel.kt` — UI state + handlers
- `android/app/src/main/java/com/example/smartphonapptest001/ui/screen/SettingsScreen.kt` — toggle + speaker dropdown (SERVER only)
- `android/app/src/main/java/com/example/smartphonapptest001/ui/SmartphoneChatApp.kt` — VoiceVox routing in speech effect
- `android/app/src/main/java/com/example/smartphonapptest001/MainActivity.kt` — callback wiring

Bug fixes during this milestone:
- `ServerTtsApi.kt` — OkHttp `toMediaType` / `toRequestBody` API usage fixed
- `ServerChatRequest.kt` — `Map<String, Any>` → `Map<String, JsonElement>` (kotlinx.serialization)
- `ServerChatApi.kt` — Ktor `readUTF8Line(Int.MAX_VALUE)` + `toJsonElement` helper

### Milestone 6: Request Flow Tracing (Done - with opencode)

```text
Every chat and TTS request gets a unique trace_id at server entry point.
Each pipeline step is recorded in request_traces table: received → ai_call → ai_response → db_saved → complete
Errors are captured with context: which step failed, error message, timing.
GET /api/logs/flow returns recent request flows with step progression.
GET /api/logs/flow/{traceId} returns detailed steps for a specific trace.
/admin/logs.html shows Request Flow section with card-based UI.
Flow cards show step-by-step progress with color coding (green=ok, red=error, gray=pending).
Status bar includes flow error count and total trace count.
```

Key files created:
- `server/src/main/java/com/example/guardianplants/RequestTraceRepository.java`
- `server/src/main/java/com/example/guardianplants/service/RequestTraceService.java`
- `db/init/002_request_traces.sql`

Key files modified:
- `server/src/main/java/com/example/guardianplants/controller/ChatController.java` — trace_id generation on entry
- `server/src/main/java/com/example/guardianplants/service/ChatService.java` — AI call/response/DB save/error tracing
- `server/src/main/java/com/example/guardianplants/controller/TtsController.java` — trace_id generation on entry
- `server/src/main/java/com/example/guardianplants/controller/LogViewerController.java` — /api/logs/flow endpoints
- `server/src/main/resources/static/admin/logs.html` — Request Flow UI section

### Milestone 7: TTS Android Fixes (Done - with opencode)

```text
ServerTtsApi.kt readTimeout increased from 60s to 120s to handle long TTS generation.
Added writeTimeout 30s for request body upload.
GuardianReplySpeechEffect MediaPlayer resource leak fixed:
  prepare() failure now properly releases MediaPlayer and deletes temp WAV file.
  try-catch ensures cleanup even on unexpected exceptions.
```

Key files modified:
- `android/app/src/main/java/com/example/smartphonapptest001/data/network/ServerTtsApi.kt` — timeout tuning
- `android/app/src/main/java/com/example/smartphonapptest001/ui/SmartphoneChatApp.kt` — MediaPlayer leak fix

## Current Architecture

```text
Android app (Jetpack Compose)
  -> VPS nginx (HTTPS planned)
    -> Spring Boot server (Java 17)
      -> PostgreSQL (chat_histories, app_logs, request_traces)
      -> VoiceVOX Engine (Docker, configurable)
      -> external AI / Ollama Cloud
  -> Browser: /admin/logs.html (dashboard, no auth, request flow tracking)
```

## VPS Deployment

- Server IP: `80.241.214.154` (configured in `android/local.properties`)
- Endpoint: `http://80.241.214.154/api/`
- Services: nginx, server, db, voicevox (all running via docker-compose)
- Setup script: `setup-env.sh`

## Build Commands

### Android APK
```powershell
cd C:\work\guardian_of_the_plants\android
$env:JAVA_HOME="C:\Program Files\Android\Android Studio1\jbr"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

### Server (Docker)
```powershell
cd C:\work\guardian_of_the_plants
docker compose up -d --build db server nginx
```

## Known Risks

| Risk | Status | Mitigation |
|------|--------|------------|
| Secrets in `.env` | Manual | Never commit `.env` |
| Android Filament native crashes | Known | 3D expression off by default |
| VoiceVOX CPU load | Expected | `VOICEVOX_ENABLED=false` to disable |
| VPS nginx HTTP (no TLS) | Planned | HTTPS/TLS next milestone |
| `local.properties` machine-specific | Info | SDK path set for `tomok`'s machine |

## Next Steps

1. Build release APK and test VoiceVox toggle on physical device (Xiaomi 2602BPC18G)
2. Add HTTPS/TLS to VPS nginx (Let's Encrypt)
3. Add server-side rate limiting for `/api/tts/synthesize`
4. Consider async TTS job queue for high-latency scenarios
5. Move RAG/knowledge management server-side
6. Add browser log viewer auth (basic auth or token)
7. Verify request_traces table auto-created via Docker init script

## Git State

```text
Repository: https://github.com/IdeaTest0409/guardian_of_the_plants.git
Branch: main
Working directory: C:\work\guardian_of_the_plants
```

All changes should be committed and pushed after verification:
```powershell
git add .
git commit -m "Describe change"
git push
```
