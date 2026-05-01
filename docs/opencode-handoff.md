# opencode handoff: guardian_of_the_plants

Last updated: 2026-05-01

## Repository

```text
https://github.com/IdeaTest0409/guardian_of_the_plants
```

Local workspace:

```text
C:\work\guardian_of_the_plants
```

Current working branch:

```text
main
```

## Current Verified Architecture

```text
Android app
  -> VPS nginx
    -> Spring Boot server container
      -> PostgreSQL container
        -> app_logs
        -> chat_histories
        -> request_traces
      -> VoiceVOX Engine container
      -> external AI provider
  -> browser admin viewer at /admin/logs.html
```

Only nginx should be published externally. `server`, `db`, and `voicevox`
should remain internal Docker services.

## Current Android Direction

Default provider:

```text
ProviderType.SERVER
```

Android API endpoint is injected at build time through:

```text
android/local.properties
guardian.api.baseUrl=http://<SERVER_HOST>/api
```

`android/local.properties` is ignored by Git. Use:

```text
android/local.properties.example
```

AI API keys should not be placed in Android source. Server-routed chat should
use server-side `.env` values.

## Completed Milestones

1. Android app-start logging to PostgreSQL.
2. Server-side chat API through `/api/chat`.
3. Server-side VoiceVOX TTS through `/api/tts/synthesize`.
4. Browser log viewer at `/admin/logs.html`.
5. Android VoiceVOX TTS integration for SERVER provider.
6. Request flow tracing in `request_traces`.
7. Android TTS timeout and MediaPlayer cleanup fixes.

See `development-status.md` for details.

## Important Docs

```text
docs/README.md
docs/development-status.md
docs/android-build.md
docs/local-api-smoke-test.md
docs/vps-smoke-test.md
docs/vps-operations.md
docs/https-tls-plan.md
docs/session-change-log.md
```

## Docker

Start or update the VPS/local stack:

```bash
docker compose up -d --build db server nginx voicevox
```

If VoiceVOX should not be used, set:

```env
VOICEVOX_ENABLED=false
```

Health checks:

```bash
curl http://localhost/health; echo
curl http://localhost/api/health; echo
curl http://localhost/api/logs/health; echo
curl http://localhost/api/tts/health; echo
```

## Database

Current tables:

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

Important limitation:

```text
db/init/*.sql runs only when PostgreSQL initializes a new volume.
Existing VPS volumes need manual migration or a migration tool.
```

## Security Notes

- Keep `.env` out of Git.
- Keep AI API keys on the server side.
- Add HTTPS/TLS before production use.
- Add authentication for `/admin/logs.html`.
- Add rate limiting for `/api/chat` and `/api/tts/synthesize`.
- Do not publish PostgreSQL, server, or VoiceVOX ports.

## Known Risk Areas

```text
Android Filament/SceneView native crashes
VoiceVOX CPU load and latency
Request trace table growth
No admin viewer authentication yet
No HTTPS/TLS yet
No formal DB migration tool yet
```

## Next Practical Work

1. Add HTTPS/TLS to VPS nginx.
2. Add authentication to `/admin/logs.html`.
3. Add rate limiting for chat and TTS endpoints.
4. Add formal DB migration workflow.
5. Decide whether Android `CLOUD` provider remains visible or becomes legacy.
6. Move RAG/knowledge management server-side.
