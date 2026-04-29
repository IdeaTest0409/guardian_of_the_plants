# Session Change Log

Created: 2026-04-30

This document summarizes all changes made during the development session.

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
