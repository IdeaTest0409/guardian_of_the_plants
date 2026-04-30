# Session Change Log

Created: 2026-04-30

This document summarizes all changes made during the development session.

---

## 3. Browser Log Viewer (2026-04-30)

> **Note:** This feature was implemented with the assistance of [OpenCode](https://opencode.ai), an AI-powered CLI coding assistant.

### New Server Files

| File | Purpose |
|------|---------|
| `server/.../LogViewerRepository.java` | DB queries for chat_histories and app_logs |
| `server/.../controller/LogViewerController.java` | REST endpoints: `GET /api/logs/chat`, `GET /api/logs/app` |
| `server/src/main/resources/static/admin/logs.html` | Browser UI: HTML + vanilla JS table display |

### Modified Files

| File | Change |
|------|--------|
| `nginx/default.conf` | Added `/admin/` proxy to server |

### New Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/logs/chat` | Returns recent chat history (default 100, max 500) |
| `GET` | `/api/logs/app` | Returns recent app logs (default 100, max 500) |

### Browser Page

Accessible at `/admin/logs.html` through nginx.

**Features:**
- Two sections: Chat History (detailed) and App Logs (simplified)
- Latest-first ordering
- Refresh buttons per section
- Mobile-friendly responsive design (columns hidden on small screens)
- Color-coded roles (user=blue, assistant=green) and severity levels

**Chat History columns:** 日時 | デバイス | 会話ID | ロール | メッセージ | ステータス

**App Logs columns:** 受信日時 | デバイス | バージョン | カテゴリ | メッセージ

---

## 2. Server-side VoiceVOX TTS Integration (2026-04-30)

> **Note:** This feature was implemented with the assistance of [OpenCode](https://opencode.ai), an AI-powered CLI coding assistant.

### New Server Files

| File | Purpose |
|------|---------|
| `server/.../config/VoiceVoxConfig.java` | `@ConfigurationProperties` for VoiceVOX settings (enabled, base-url) |
| `server/.../dto/TtsRequest.java` | TTS request DTO: `text` (String), `speaker` (int) |
| `server/.../service/TtsService.java` | VoiceVOX communication: `/audio_query` → `/synthesis` → WAV return |
| `server/.../controller/TtsController.java` | `POST /api/tts/synthesize`, `GET /api/tts/speakers` endpoints |

### Modified Files

| File | Change |
|------|--------|
| `docker-compose.yml` | Removed `profiles: [voice]` from voicevox service (always starts); added `VOICEVOX_ENABLED` and `VOICEVOX_BASE_URL` env var mapping; added `voicevox_data` volume |
| `.env.example` | Added `VOICEVOX_ENABLED=true` |
| `server/src/main/resources/application.yml` | Added `voicevox.enabled` and `voicevox.base-url` properties |
| `server/.../service/ChatService.java` | Fixed compilation errors: `HttpStatus::isError` → `status -> status.isError()`; wrapped `emitter.send()` in try-catch for `IOException` |

### New Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/tts/synthesize` | Synthesize speech via VoiceVOX. Returns WAV audio. |
| `GET` | `/api/tts/speakers` | List available VoiceVOX speakers (proxied from VoiceVOX engine). |

### Request/Response

**POST /api/tts/synthesize**

Request body:
```json
{ "text": "こんにちは", "speaker": 2 }
```

Response:
- Success: `200 OK`, `Content-Type: audio/wav`, WAV binary
- Disabled: `400 Bad Request`, `{"error":"TTS is disabled"}`
- VoiceVOX error: `502 Bad Gateway`, `{"error":"VoiceVOX unavailable: ..."}`

**GET /api/tts/speakers**

Response:
- Success: `200 OK`, JSON array of speaker objects (name, styles, ids, etc.)
- Disabled: `400 Bad Request`, `{"error":"TTS is disabled"}`

### VoiceVOX Communication Flow

```
1. POST {voicevox_url}/audio_query?text={text}&speaker={speaker}
   → Returns audio query JSON (phoneme timing, pitch, etc.)
2. POST {voicevox_url}/synthesis?speaker={speaker}
   Content-Type: application/json
   Body: audio query JSON from step 1
   → Returns WAV binary
```

### Architecture

```
Android → nginx → Spring Boot server → VoiceVOX Engine (Docker)
                                         ↓
                                    WAV audio
                                         ↓
                                    Android playback
```

### TTS Enable/Disable Control

- `VOICEVOX_ENABLED=true` → TTS endpoints active
- `VOICEVOX_ENABLED=false` → Returns error response (saves CPU resources)

### Verification

- `GET /api/tts/speakers` → 33 speakers returned (四国めたん, ずんだもん, 春日部つむぎ, etc.)
- `POST /api/tts/synthesize` with `speaker=2` → 45KB WAV file generated with valid RIFF header

---

## 1. Server-Side Chat API with SSE Proxy (Commit: 871b059)

### New Server Files

| File | Purpose |
|------|---------|
| `server/src/main/java/com/example/guardianplants/controller/ChatController.java` | `POST /api/chat` endpoint accepting SSE requests |
| `server/src/main/java/com/example/guardianplants/service/ChatService.java` | SSE proxy: forwards to AI provider, streams back to Android, saves to DB |
| `server/src/main/java/com/example/guardianplants/service/ProviderResolver.java` | Reads AI provider config (model, API key, base URL) from `.env` |
| `server/src/main/java/com/example/guardianplants/dto/ChatRequest.java` | Request DTO: `messages`, `deviceId`, `conversationId`, `options` |
| `server/src/main/java/com/example/guardianplants/dto/ServerMessage.java` | Message DTO: `role`, `content` |
| `server/src/main/java/com/example/guardianplants/ChatHistoryRepository.java` | JDBC-based repository for `chat_histories` table |

### Server Dependencies

- Added `spring-boot-starter-webflux` to `server/build.gradle.kts` for `WebClient` (reactive SSE client to upstream AI provider)

### Server Config

- `server/src/main/resources/application.yml`: Added `spring.webflux` and `server.address: 0.0.0.0`

### Android Changes

| File | Change |
|------|--------|
| `android/app/src/main/java/.../model/ProviderType.kt` | Added `SERVER` provider type |
| `android/app/src/main/java/.../network/ServerChatApi.kt` | New: SSE HTTP client for `/api/chat` using `OkHttp` |
| `android/app/src/main/java/.../network/ServerChatRequest.kt` | New: request model for server API |
| `android/app/src/main/java/.../repository/DefaultChatRepository.kt` | Added `SERVER` routing logic |
| `android/app/src/main/java/.../ui/screen/SettingsScreen.kt` | Added `SERVER` option to provider selector |
| `android/app/src/main/java/.../ui/screen/ChatScreen.kt` | Updated to handle `SERVER` provider |
| `android/app/src/main/java/.../viewmodel/ChatViewModel.kt` | Updated to route through `SERVER` when selected |

### Infrastructure

- `docker-compose.yml`: Updated `server` service to pass `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL` environment variables
- `setup-env.sh`: New interactive script for VPS `.env` generation

### Architecture

```
Android (SERVER) -> nginx -> server (SseEmitter) -> WebClient -> AI provider (Ollama Cloud)
                                           |
                                           v
                                   chat_histories (PostgreSQL)
```

---

## 2. Java 17 Compatibility Fix (Commit: 22a0a90)

- `ChatService.java`: Replaced `List.getLast()` (Java 21+) with `list.get(list.size() - 1)` (Java 17 compatible)

---

## 3. Upstream API Error Handling (Commit: 11371a9)

- `ChatService.java`: Added `onStatus(HttpStatus::isError, ...)` to `WebClient` chain
- Returns structured error via SSE: `{"error":"AI API key is not configured"}` or upstream error response

---

## 4. Docker Networking Fix (Commit: 37308a9)

- `server/src/main/resources/application.yml`: Added `server.address: 0.0.0.0`
- Without this, Spring Boot binds to `localhost` by default, making it unreachable from nginx on the Docker network

---

## 5. AI Provider Env Vars to Container (Commit: 7dd0f7e)

- `docker-compose.yml`: Explicitly mapped `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL` into the `server` container's environment

---

## 6. .env.example Password Alignment (Commit: dab4091)

- `.env.example`: Aligned `POSTGRES_PASSWORD` with `docker-compose.yml` default value (`guardian_password`)

---

## VPS Verification Results

| Check | Result |
|-------|--------|
| SSE streaming via curl | ✅ Works — AI returns Japanese text about Sansevieria |
| `chat_histories` user rows | ✅ Saved (8 rows confirmed) |
| `chat_histories` assistant rows | ❌ **NOT saved** — zero rows |
| Server error logs | None visible at default log level |

---

## Known Issues

### Assistant Messages Not Saved to DB

- `user` role messages are saved successfully
- `assistant` role messages are **never persisted**
- No error or warning appears in server logs
- Likely cause: SSE JSON parsing silently fails (log level is `DEBUG`), or the Ollama Cloud SSE format differs from the expected OpenAI-compatible structure
- Debug plan documented in `docs/assistant-db-save-debug.md`

### Conversation History Not Retrieved

- `ChatService.buildUpstreamRequest()` only uses `request.messages()` from the current request
- Past messages from `chat_histories` are never fetched or prepended
- This means the AI has no conversation context across requests

---

## Files Changed (Summary)

### Created
- `server/src/main/java/com/example/guardianplants/controller/ChatController.java`
- `server/src/main/java/com/example/guardianplants/service/ChatService.java`
- `server/src/main/java/com/example/guardianplants/service/ProviderResolver.java`
- `server/src/main/java/com/example/guardianplants/dto/ChatRequest.java`
- `server/src/main/java/com/example/guardianplants/dto/ServerMessage.java`
- `server/src/main/java/com/example/guardianplants/ChatHistoryRepository.java`
- `android/app/src/main/java/.../network/ServerChatApi.kt`
- `android/app/src/main/java/.../network/ServerChatRequest.kt`
- `setup-env.sh`
- `docs/assistant-db-save-debug.md`
- `docs/session-change-log.md` (this file)

### Modified
- `server/build.gradle.kts` (added webflux dependency)
- `server/src/main/resources/application.yml` (0.0.0.0 binding)
- `docker-compose.yml` (env var mapping)
- `android/app/src/main/java/.../model/ProviderType.kt`
- `android/app/src/main/java/.../repository/DefaultChatRepository.kt`
- `android/app/src/main/java/.../ui/screen/SettingsScreen.kt`
- `android/app/src/main/java/.../ui/screen/ChatScreen.kt`
- `android/app/src/main/java/.../viewmodel/ChatViewModel.kt`
- `.env.example`
