# Local API smoke test

This document checks the first backend path:

```text
Android app start
  -> nginx
    -> Spring Boot server
      -> PostgreSQL app_logs
```

## Start Docker Services

Run the services needed for this smoke test:

```powershell
cd C:\work\guardian_of_the_plants
docker compose up -d --build db server nginx
docker compose ps
```

`voicevox` is profile-gated and is not needed for this test.

Only nginx is published to the host. Spring Boot and PostgreSQL communicate on
the internal Docker Compose network.

Check nginx itself:

```powershell
curl http://localhost/health
```

Expected:

```text
nginx ok
```

## Check API Through nginx

```powershell
curl http://localhost/api/health
```

Expected JSON:

```json
{"status":"ok"}
```

## Insert App Start Log By curl

```powershell
$body = @{
  deviceId = "curl-test"
  appVersion = "0.0.0"
  details = @{
    source = "manual-smoke-test"
  }
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri http://localhost/api/app-start `
  -ContentType "application/json" `
  -Body $body
```

Expected JSON:

```json
{"id":1,"status":"stored"}
```

The `id` value may differ.

## Verify DB Row

```powershell
docker exec -it guardian-postgres psql -U guardian_user -d guardian_plants `
  -c "select id, device_id, app_version, severity, category, message, received_at from app_logs order by id desc limit 5;"
```

Expected:

```text
category  | APP_START
message   | Android app started
```

## Configure Android App Start Reporting

Android does not read the repository root `.env` at runtime.

For this temporary app-start reporting feature, set the API URL through
`android/local.properties` or a Gradle property. This keeps the URL out of Git
and avoids adding UI settings.

Use the template:

```powershell
Copy-Item android\local.properties.example android\local.properties
```

Then set `guardian.api.baseUrl`.

Example for Android emulator:

```properties
guardian.api.baseUrl=http://10.0.2.2/api
```

Example for a physical device on the same LAN:

```properties
guardian.api.baseUrl=http://<PC_LAN_IP>/api
```

If `guardian.api.baseUrl` is blank or missing, Android skips app-start reporting.

Build after setting the value:

```powershell
cd C:\work\guardian_of_the_plants\android
.\gradlew.bat assembleDebug
```

You can also pass the URL without editing `local.properties`:

```powershell
.\gradlew.bat assembleDebug -PguardianApiBaseUrl=http://10.0.2.2/api
```

The app sends `POST /api/app-start` once during `MainActivity.onCreate`.
Failures are logged locally and do not block the UI.

## Stop Services

Stop containers while keeping PostgreSQL data:

```powershell
docker compose down
```

Stop containers and reset PostgreSQL data:

```powershell
docker compose down -v
```
