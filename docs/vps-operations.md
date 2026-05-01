# VPS Operations

Operational commands for the Ubuntu VPS deployment.

## Update Code And Restart

```bash
cd ~/guardian_of_the_plants
git pull
docker compose up -d --build db server nginx
docker compose ps
```

Include VoiceVOX:

```bash
docker compose up -d --build db server nginx voicevox
```

## Health Checks

```bash
curl http://localhost/health; echo
curl http://localhost/api/health; echo
curl http://localhost/api/logs/health; echo
curl http://localhost/api/tts/health; echo
```

## Logs

```bash
docker logs guardian-nginx --tail 100
docker logs guardian-server --tail 100
docker logs guardian-postgres --tail 100
docker logs guardian-voicevox --tail 100
```

Follow server logs:

```bash
docker logs -f guardian-server
```

## Database Checks

```bash
docker exec guardian-postgres psql -U guardian_user -d guardian_plants \
  -c "select id, device_id, app_version, severity, category, message, received_at from app_logs order by id desc limit 10;"
```

```bash
docker exec guardian-postgres psql -U guardian_user -d guardian_plants \
  -c "select id, device_id, conversation_id, role, left(content, 80), created_at from chat_histories order by id desc limit 10;"
```

```bash
docker exec guardian-postgres psql -U guardian_user -d guardian_plants \
  -c "select trace_id, request_type, step, status, created_at from request_traces order by id desc limit 20;"
```

## Stop

Keep DB data:

```bash
docker compose down
```

Reset DB data:

```bash
docker compose down -v
```

## Backup PostgreSQL

```bash
docker exec guardian-postgres pg_dump -U guardian_user guardian_plants > guardian_plants_backup.sql
```

Restore into a fresh database only after reviewing the target environment:

```bash
cat guardian_plants_backup.sql | docker exec -i guardian-postgres psql -U guardian_user -d guardian_plants
```

## Security Checklist

- Keep only nginx published externally.
- Do not publish PostgreSQL, server, or VoiceVOX ports.
- Keep `.env` off Git.
- Add HTTPS/TLS.
- Add authentication for `/admin/logs.html`.
- Add rate limiting for chat and TTS endpoints.
