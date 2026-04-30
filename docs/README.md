# Project docs

Use these documents by purpose:

```text
development-status.md   Current milestone status, architecture, and next steps.
android-build.md        Build the Android APK.
android-migration-list.md Historical Android migration notes.
local-api-smoke-test.md Local Docker/nginx/server/db smoke test.
vps-smoke-test.md       Ubuntu VPS deployment and smoke test.
opencode-handoff.md     Broad project handoff and current status.
```

Current verified milestone:

```text
Android app with VoiceVox TTS toggle (SERVER provider only)
  -> VPS nginx
    -> server container
      -> PostgreSQL container (app_logs, chat_histories, request_traces)
      -> VoiceVOX Engine (3 speakers)
  -> /admin/logs.html (browser log viewer with health status, auto-refresh, request flow tracking)
```

Request Flow Tracing (Milestone 6):
```text
Every request gets a trace_id. Pipeline steps tracked:
  Chat: received → ai_call → ai_response → db_saved → complete/error
  TTS:  received → voicevox_call → voicevox_response → complete/error
GET /api/logs/flow — recent flows
GET /api/logs/flow/{traceId} — detailed steps
```

All development from Milestone 2 onward was done with **OpenCode** ([opencode.ai](https://opencode.ai)), an AI-powered CLI coding assistant.

See `development-status.md` for the full milestone list, architecture, and next steps.
