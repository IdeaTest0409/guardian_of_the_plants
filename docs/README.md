# Project docs

Use these documents by purpose:

```text
android-build.md          Build the Android APK.
android-migration-list.md Historical Android migration notes.
local-api-smoke-test.md   Local Docker/nginx/server/db smoke test.
vps-smoke-test.md         Ubuntu VPS deployment and smoke test.
opencode-handoff.md       Broad project handoff and current status.
```

Current verified milestone:

```text
Android app start
  -> VPS nginx
    -> server container
      -> PostgreSQL container
        -> app_logs
```

Verified rows included:

```text
device_id   Xiaomi/2602BPC18G
app_version 1.0.0
severity    INFO
category    APP_START
message     Android app started
```
