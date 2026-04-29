# Ubuntu VPS smoke test

This document records the current VPS path that has been verified.

## Target Path

```text
Android app start
  -> nginx on VPS
    -> server container
      -> PostgreSQL container
        -> app_logs
```

Only nginx is published to the host. `server`, `db`, and future `voicevox`
services communicate on the Docker Compose internal network.

## Prerequisites

On the VPS:

```bash
docker --version
docker compose version
```

Verified example:

```text
Docker version 29.4.1
Docker Compose version v5.1.3
```

The user must either run Docker commands with `sudo` or belong to the `docker`
group.

## Clone Or Update

```bash
cd ~
git clone https://github.com/IdeaTest0409/guardian_of_the_plants.git
cd guardian_of_the_plants
```

If already cloned:

```bash
cd ~/guardian_of_the_plants
git pull
```

## Configure Environment

```bash
cp .env.example .env
nano .env
```

At minimum, set a real PostgreSQL password:

```env
POSTGRES_DB=guardian_plants
POSTGRES_USER=guardian_user
POSTGRES_PASSWORD=<strong_password>
```

Do not commit `.env`.

## Reset And Start From Scratch

Run from the directory containing `docker-compose.yml`:

```bash
cd ~/guardian_of_the_plants
docker compose down -v
docker compose up -d --build db server nginx
docker compose ps
```

If Docker permission is denied, use `sudo docker compose ...` or add the user to
the `docker` group and reconnect.

Expected services:

```text
guardian-postgres   Up / healthy
guardian-server     Up
guardian-nginx      Up
```

## nginx Health

On the VPS:

```bash
curl http://localhost/health; echo
```

Expected:

```text
nginx ok
```

From an external machine:

```powershell
curl.exe http://<VPS_IP>/health
```

## Server Health Through nginx

On the VPS:

```bash
curl http://localhost/api/health; echo
```

Expected:

```json
{"status":"ok"}
```

From an external machine:

```powershell
curl.exe http://<VPS_IP>/api/health
```

## Manual App Start Insert

On the VPS:

```bash
curl -X POST http://localhost/api/app-start \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"vps-test","appVersion":"0.0.0","details":{"source":"vps-smoke-test"}}'
```

Expected:

```json
{"status":"stored","id":1}
```

The `id` may differ if the database was not reset.

## DB Verification

```bash
docker exec guardian-postgres psql -U guardian_user -d guardian_plants \
  -c "select id, device_id, app_version, severity, category, message, received_at from app_logs order by id desc limit 5;"
```

Expected rows:

```text
device_id | vps-test
category  | APP_START
message   | Android app started
```

## Android Configuration

On the Android build machine, copy the template:

```powershell
cd C:\work\guardian_of_the_plants
Copy-Item android\local.properties.example android\local.properties
```

Set the VPS API endpoint:

```properties
guardian.api.baseUrl=http://<VPS_IP>/api
```

Build and install the APK:

```powershell
cd C:\work\guardian_of_the_plants\android
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME='C:\Users\tomok\AppData\Local\Android\Sdk'
.\gradlew.bat assembleDebug
```

Expected Android logs after app launch:

```text
INFO / AppStartReporter: Guardian API base URL configured
INFO / AppStartReporter: Guardian API health check completed
INFO / AppStartReporter: Server app-start report completed
```

Then verify the VPS DB. Verified smartphone rows included:

```text
device_id   Xiaomi/2602BPC18G
app_version 1.0.0
severity    INFO
category    APP_START
message     Android app started
```

## Logs

```bash
docker logs guardian-nginx --tail 100
docker logs guardian-server --tail 100
docker logs guardian-postgres --tail 100
```

Follow server logs:

```bash
docker logs -f guardian-server
```

## Stop

Stop containers while keeping DB data:

```bash
docker compose down
```

Stop and reset DB data:

```bash
docker compose down -v
```

## Security Notes

Current Compose target:

```text
external network -> nginx only
nginx -> server over Docker internal network
server -> db over Docker internal network
```

Do not publish PostgreSQL directly on the VPS.

Before real production use:

- Add HTTPS/TLS.
- Move any production secrets to the VPS `.env`.
- Keep AI API keys out of Android source.
- Add server-side validation and basic rate limiting.
