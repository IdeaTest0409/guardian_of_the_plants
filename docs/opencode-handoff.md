# opencode handoff: guardian_of_the_plants

Last updated: 2026-04-30 (Android VoiceVox Integration)

## Current Repository State

Repository:

```text
https://github.com/IdeaTest0409/guardian_of_the_plants
```

Local working directory:

```text
C:\work\guardian_of_the_plants
```

Git state before adding this handoff document:

```text
branch: main
remote: origin https://github.com/IdeaTest0409/guardian_of_the_plants.git
base commit: 6ed1004 Initial guardian of the plants project
status: main was synced with origin/main before this document was added
```

Important history:

- This project was originally inside a parent Git repository at `C:\work`.
- It has now been split into its own standalone Git repository at `C:\work\guardian_of_the_plants`.
- The GitHub repository `IdeaTest0409/guardian_of_the_plants` has been created and pushed.
- GitHub CLI was installed at `C:\Program Files\GitHub CLI\gh.exe`.
- GitHub CLI authentication was completed for user `IdeaTest0409`.

## Security Cleanup Already Done

Before the first commit, an Ollama Cloud API key had been found hardcoded in:

```text
android\app\src\main\java\com\example\smartphonapptest001\data\model\AppSettings.kt
```

It was changed to an empty default:

```kotlin
const val DEFAULT_OLLAMA_CLOUD_API_KEY = ""
```

Do not restore an API key into source code.

The real runtime secrets should stay in:

```text
.env
```

The `.env` file is intentionally ignored by Git. The shared template is:

```text
.env.example
```

Before future public pushes, check again for secrets:

```powershell
cd C:\work\guardian_of_the_plants
git status --ignored --short
Select-String -Path android\app\src\main\java\com\example\smartphonapptest001\data\model\AppSettings.kt -Pattern 'API_KEY|sk-|Bearer|67615eb9' -CaseSensitive:$false
```

## Directory Structure

```text
guardian_of_the_plants/
  android/              Android app migrated from smartphonapptest001
  db/
    init/
      001_init.sql      PostgreSQL initial schema
  docs/
    README.md
    android-migration-list.md
    android-build.md
    local-api-smoke-test.md
    vps-smoke-test.md
    opencode-handoff.md
  nginx/
    default.conf        Local nginx reverse proxy config
  server/               Spring Boot server (JDK 17)
    src/.../guardianplants/
      GuardianPlantsServerApplication.java
      ChatHistoryRepository.java
      LogViewerRepository.java            GET /api/logs/*
      config/VoiceVoxConfig.java           VoiceVOX settings
      config/WebClientConfig.java
      controller/ChatController.java       POST /api/chat (SSE)
      controller/TtsController.java        POST /api/tts/synthesize, GET /api/tts/speakers
      controller/LogViewerController.java  GET /api/logs/chat, GET /api/logs/app
       dto/ChatRequest.java
       dto/ServerMessage.java
       dto/TtsRequest.java
       service/ChatService.java             SSE proxy + DB storage
       service/TtsService.java              VoiceVOX audio_query → synthesis
       service/TtsHealthService.java        VoiceVOX health check
       service/ProviderResolver.java        .env provider config
     src/main/resources/static/admin/
       logs.html                            Browser log viewer UI
  .env                  Local secrets, ignored by Git
  .env.example          Shared environment template
  .gitattributes
  .gitignore
  docker-compose.yml
```

Ignored generated/local paths include:

```text
.env
android/.gradle/
android/.kotlin/
android/build/
android/app/build/
android/local.properties
*.apk
```

Note: `android/.gradle/` and `android/build/` existed locally during setup, but they are ignored and were not committed.

## Project Goal

Build an Android app for ornamental plant care where a guardian angel or butler character talks about photographed plants, reads responses aloud, and eventually stores logs and conversation history on a VPS-backed server.

Long-term target architecture:

```text
Android app
  -> nginx
    -> Spring Boot server
      -> PostgreSQL
      -> VOICEVOX Engine
      -> external AI / Ollama Cloud
```

Verified milestones:

```text
Android app sends one log entry to the server.
Server stores it in PostgreSQL app_logs.

Android app sends chat messages to /api/chat via SSE.
Spring Boot proxies to AI provider (Ollama Cloud / configured provider).
PostgreSQL chat_histories stores user + assistant messages.
No AI API keys on the Android device.
```

This has been verified locally and on an Ubuntu VPS. Smartphone app-start rows
from `Xiaomi/2602BPC18G` reached `app_logs` through:

```text
Android -> VPS nginx -> server container -> PostgreSQL container
```

Android `ProviderType.SERVER` provider added. Chat routes through:

```text
Android (SERVER) -> nginx -> server (SSE proxy) -> AI provider (configured in .env)
```

Existing LOCAL / CLOUD / OLLAMA_CLOUD providers remain functional.

## Android App State

Android project path:

```text
C:\work\guardian_of_the_plants\android
```

Original source project at migration time:

```text
C:\work\test001\smartphonapptest001
```

This path was only the source used to copy the Android project into this
repository. The active Android project is `C:\work\guardian_of_the_plants\android`.
The old `C:\work\test001\smartphonapptest001` directory can be deleted after the
migrated project is confirmed to build.

Migrated items:

```text
app/
gradle/
build.gradle.kts
settings.gradle.kts
gradle.properties
README.md
.gitattributes
.gitignore
```

Not migrated intentionally:

```text
.git/
.gradle/
.kotlin/
build/
app/build/
local.properties
*.apk
```

`local.properties` is not committed because it contains a machine-specific Android SDK path. Recreate it via Android Studio or manually:

```properties
sdk.dir=C\:\\Users\\tomok\\AppData\\Local\\Android\\Sdk
```

Known Android features:

- Native Android app with Jetpack Compose.
- Plant conversation UI.
- Guardian personalities: `ANGEL` and `BUTLER`.
- Presentation modes: 2D, 3D, MR camera.
- AI providers: local LiteRT-LM, LM Studio-compatible cloud, Ollama Cloud.
- Image input: camera capture, file picker, real-time camera.
- Android TextToSpeech.
- Log save/share.
- Crash recovery logs.
- Quick prompt buttons.
- Voice input.
- Auto small talk.
- Sansevieria knowledge file used as RAG-like context.

Knowledge file:

```text
android\app\src\main\assets\knowledge\sansevieria_knowledge.txt
```

Knowledge repository:

```text
android\app\src\main\java\com\example\smartphonapptest001\data\knowledge\PlantKnowledgeRepository.kt
```

## MR / 3D Notes

Realtime camera MR has worked before.

Static image plus SceneView 3D overlay was unstable. Previously attempted approaches:

- Compose `Image` background plus SceneView.
- Android `ImageView` background plus SceneView.
- SceneView `ImageNode` for still-image background.

Observed problems:

- Compose/ImageView background could become black behind SceneView.
- SceneView `ImageNode` triggered native crashes in `libfilament-jni.so`.

Current intended behavior:

- Use MR 3D only for real-time camera mode.
- For captured still image or uploaded file, fall back to still image plus 2D guardian.
- Do not reintroduce the `ImageNode` still-image background approach without careful native crash testing.

Important files:

```text
android\app\src\main\java\com\example\smartphonapptest001\ui\component\GuardianMixedRealityStage.kt
android\app\src\main\java\com\example\smartphonapptest001\ui\screen\ChatScreen.kt
```

Current MR initial values:

```text
X    = -0.34
Y    = -0.32
Size = 1.14
Dist = 0.43
Yaw  = 0.00
Tilt = 40.00
```

3D expression driving is risky:

- Default should remain off.
- Filament morph target access has caused native crashes before.
- `Fcl_*` morph target names may need special handling.
- In MR, only blinking/lip sync should be tested conservatively.

## AI / Ollama Cloud State

Current default provider:

```text
Provider = OLLAMA_CLOUD
Model    = gemma4:31b-cloud
Base URL = https://ollama.com/v1
```

Currently enabled model:

```text
gemma4:31b-cloud
```

Models shown but intentionally not selectable:

```text
kimi-k2.6:cloud
qwen3.6:35b
```

Reason:

- `gemma4:31b-cloud` appeared to work.
- `kimi-k2.6:cloud` and `qwen3.6:35b` were unresponsive or unstable.

Important settings file:

```text
android\app\src\main\java\com\example\smartphonapptest001\data\model\AppSettings.kt
```

DataStore settings repository:

```text
android\app\src\main\java\com\example\smartphonapptest001\data\SettingsRepository.kt
```

DataStore name:

```text
smartphone_app_settings
```

Key:

```text
ollama_cloud_api_key
```

Security direction:

- Do not put AI API keys in Android source.
- Short term: user/device setting may hold a key locally.
- Long term: move AI key handling to the Spring Boot server and let Android call only the app's own API.

## TTS / Audio State

Current audio:

- **Device TextToSpeech**: Android internal TTS (default for all providers).
- **Server VoiceVox TTS**: Available for SERVER provider via toggle in Settings.
  - `Speak guardian replies` must be ON.
  - `Use VoiceVox TTS` toggle appears only for SERVER provider.
  - 3 selectable speakers: 四国めたん (2), ずんだもん (3), 春日部つむぎ (8).
  - Speech speed setting still applies to device TTS only.
- Default speech speed: `1.2x`.

Voice profile UI was simplified into guardian personality settings:

- Guardian personality.
- Speak guardian replies.
- Speech speed.
- (SERVER only) Use VoiceVox TTS + VoiceVox speaker selection.

Architecture:

```text
Android -> Spring Boot -> VOICEVOX Engine (Docker)
```

VOICEVOX is controlled via `VOICEVOX_ENABLED` in `.env`.

## Auto Small Talk State

Implemented:

- Auto small talk.
- Default interval: 1 minute.
- Avoids repeating similar topics.
- Expands worldbuilding: other plants are protected by guardian angels of friends.
- Includes plant trivia.
- Uses Sansevieria knowledge as RAG-like context.

Related files:

```text
android\app\src\main\assets\knowledge\sansevieria_knowledge.txt
android\app\src\main\java\com\example\smartphonapptest001\data\knowledge\PlantKnowledgeRepository.kt
```

## Docker State

Compose file:

```text
docker-compose.yml
```

Services:

- `nginx`
- `db`
- `server`
- `voicevox`

Normal local startup:

```powershell
cd C:\work\guardian_of_the_plants
docker compose up -d --build db server nginx
docker compose ps
curl http://localhost/health
```

Expected health response:

```text
nginx ok
```

VoiceVOX startup (now default — no profile needed):

```powershell
docker compose up -d
```

VoiceVOX is controlled via `VOICEVOX_ENABLED` in `.env`. Set to `false` to disable TTS and save CPU resources.

### nginx

Config:

```text
nginx\default.conf
```

Current `/api/` proxy target:

```nginx
proxy_pass http://server:8080;
```

Current meaning:

- nginx runs in Docker.
- Spring Boot runs as the `server` Compose service.
- nginx reaches Spring Boot through the internal Docker network.
- Only nginx should be published to the host.

### PostgreSQL

Image:

```text
postgres:16-alpine
```

Default DB:

```text
guardian_plants
```

Default user:

```text
guardian_user
```

Default password:

```text
guardian_password
```

Volume:

```text
postgres_data:/var/lib/postgresql/data
```

Initial SQL:

```text
db\init\001_init.sql
```

## Database Schema

Initial schema includes two tables.

### app_logs

Minimal table for Android-to-server logging.

Main columns:

```text
id
device_id
app_version
severity
category
message
details JSONB
occurred_at
received_at
```

Use this first.

### chat_histories

Future table for conversation history.

Main columns:

```text
id
device_id
conversation_id
role
content
metadata JSONB
created_at
```

Do not prioritize this until app log sending works.

## Server State

Path:

```text
C:\work\guardian_of_the_plants\server
```

Current state:

```text
Spring Boot project exists.
```

Current server APIs:

```text
GET  /api/health
GET  /api/tts/health            (VoiceVOX version check)
POST /api/app-start
POST /api/chat                  (SSE proxy to AI provider)
POST /api/tts/synthesize        (VoiceVOX TTS → WAV)
GET  /api/tts/speakers          (List available VoiceVOX speakers)
GET  /api/logs/chat             (Chat history for browser viewer)
GET  /api/logs/app              (App logs for browser viewer)
GET  /api/logs/health           (Server/DB status, log counts, error counts)
GET  /admin/logs.html           (Browser log viewer UI)
```

Example app-start request body:

```json
{
  "deviceId": "android-test",
  "appVersion": "1.0.0",
  "details": {}
}
```

Current behavior:

- Stores `APP_START` rows into `app_logs`.
- Returns a simple success response with the inserted ID.
- Detailed local verification steps are in `docs\local-api-smoke-test.md`.
- VPS verification steps are in `docs\vps-smoke-test.md`.

## Android Build Notes

Detailed APK build instructions are in:

```text
docs\android-build.md
```

The repository includes the Android Gradle Wrapper:

```text
android\gradlew
android\gradlew.bat
android\gradle\wrapper\gradle-wrapper.jar
android\gradle\wrapper\gradle-wrapper.properties
```

Build debug APK from the Android project directory:

```bash
cd android
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
cd android
.\gradlew.bat assembleDebug
```

Expected debug APK output:

```text
android\app\build\outputs\apk\debug\app-debug.apk
```

`local.properties` is not committed. Android Studio can recreate it, or it can
be generated with the local Android SDK path. Do not commit APK outputs,
`local.properties`, `.gradle/`, or build directories.

## Recommended Next Work

Do not start with the full VPS architecture. Proceed in small validated steps.

1. ~~Confirm Android `SERVER` provider builds and runs with configured endpoint.~~ → Done.
2. ~~Verify `POST /api/chat` SSE streaming through nginx to server to AI provider.~~ → Done.
3. ~~Verify `chat_histories` rows are inserted in PostgreSQL during/after streaming.~~ → Done.
4. Build release APK and test VoiceVox toggle on physical device (Xiaomi 2602BPC18G).
5. Add HTTPS/TLS for the VPS nginx endpoint (Let's Encrypt).
6. Add server-side rate limiting for `/api/tts/synthesize`.
7. Consider async TTS job queue for high-latency scenarios.
8. Later, move RAG/knowledge management server-side.
9. Add browser log viewer auth (basic auth or token).

## Completed Milestones

### Milestone 1: App Start Logging

```text
Android app sends one log entry.
Spring Boot receive it.
PostgreSQL app_logs stores it.
The Android app still works even if the server is unavailable.
```

### Milestone 4: Browser Log Viewer (2026-04-30)

> Implemented with the assistance of [OpenCode](https://opencode.ai), an AI-powered CLI coding assistant.

```text
GET /api/logs/chat returns recent chat history from PostgreSQL.
GET /api/logs/app returns recent app logs from PostgreSQL.
/admin/logs.html serves a browser UI with two tables: Chat History and App Logs.
Each section has a Refresh button for manual data reload.
Mobile-friendly responsive design. No authentication required (dashboard mode).
```

### Milestone 3: Server-side VoiceVOX TTS (2026-04-30)

> Implemented with the assistance of [OpenCode](https://opencode.ai), an AI-powered CLI coding assistant.

```text
Server exposes POST /api/tts/synthesize for VoiceVOX text-to-speech.
POST /api/tts/synthesize accepts { text, speaker } and returns WAV audio.
GET /api/tts/speakers lists available VoiceVOX speakers.
VoiceVOX Engine runs as a Docker container (default start, no profile needed).
VOICEVOX_ENABLED in .env controls TTS availability.
nginx /api/ proxy covers both /api/chat and /api/tts.
```

### Milestone 2: Server-Side Chat API (2026-04-30)

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

### Milestone 5: Android VoiceVox Integration (2026-04-30)

> Implemented with the assistance of [OpenCode](https://opencode.ai), an AI-powered CLI coding assistant.

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

Bug fixes during this milestone:
- `ServerTtsApi.kt` — OkHttp `toMediaType` / `toRequestBody` API usage fixed
- `ServerChatRequest.kt` — `Map<String, Any>` → `Map<String, JsonElement>` (kotlinx.serialization)
- `ServerChatApi.kt` — Ktor `readUTF8Line(Int.MAX_VALUE)` + `toJsonElement` helper

## Git Commands For opencode

Clone:

```powershell
git clone https://github.com/IdeaTest0409/guardian_of_the_plants.git
cd guardian_of_the_plants
```

Check state:

```powershell
git status --short --branch
git remote -v
git log --oneline -5
```

Push later changes:

```powershell
git add <files>
git commit -m "Describe change"
git push
```

GitHub CLI location on the current Windows machine:

```text
C:\Program Files\GitHub CLI\gh.exe
```

Example:

```powershell
& 'C:\Program Files\GitHub CLI\gh.exe' auth status
```

## Main Risks

### Secrets

- `.env` may contain real secrets.
- Never commit `.env`.
- Never put AI API keys back into Android source.
- Check `AppSettings.kt` before publishing changes.

### Android MR / Filament

- Avoid still-image SceneView MR experiments unless specifically testing native crash risk.
- Do not reintroduce SceneView `ImageNode` for static background without careful validation.
- Keep 3D expression/morph target features conservative.

### Generated Files

Do not commit:

```text
android/.gradle/
android/build/
android/app/build/
android/local.properties
*.apk
```

### VOICEVOX

- CPU-heavy.
- May need async job flow.
- Do not block Android UI while generating audio.

## Remaining Homework

### Publish Only nginx Externally

Current target security posture:

```text
external network -> nginx only
nginx -> server on internal Docker network
server -> db on internal Docker network
server -> voicevox on internal Docker network, when VOICEVOX is enabled
```

Keep this posture when adding future services:

- Keep `ports` only on `nginx`.
- Do not add `ports` to `server`.
- Do not publish `db` or `voicevox` directly.
- Use internal Compose service names such as `server`, `db`, and `voicevox`.
- Keep DB checks available through `docker exec guardian-postgres psql ...`.

### VPS Readiness

- Add HTTPS/TLS handling at nginx.
- Move production secrets to `.env` on the VPS, never Git.
- Recheck that API keys are not present in Android source.
- Add server-side validation and basic rate limiting before exposing wider APIs.

## Current Practical Summary

This repository is now ready for normal development as:

```text
IdeaTest0409/guardian_of_the_plants
```

The Android app has been migrated and includes server-side VoiceVox TTS integration
(3 speakers: 四国めたん, ずんだもん, 春日部つむぎ) with toggle between device TTS
and VoiceVox for SERVER provider. Docker Compose runs nginx, the Spring Boot server,
PostgreSQL, and VoiceVOX Engine. The verified backend milestones are:
Android app-start logging into PostgreSQL, server-side chat API with SSE proxy,
server-side VoiceVOX TTS synthesis, browser-based log viewer at `/admin/logs.html`,
and Android VoiceVox integration. The next practical steps are: build and test on
physical device, add HTTPS/TLS, and add server-side rate limiting.
