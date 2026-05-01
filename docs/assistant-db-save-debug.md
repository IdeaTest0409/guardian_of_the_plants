# Assistant Message DB Save Debugging Plan

Created: 2026-04-30

Status as of 2026-05-01: historical note. Request flow tracing and assistant
message save handling have since been added. Use this document only if
assistant rows disappear again.

## Original Problem

`chat_histories` showed `user` role rows but no `assistant` role rows.

Check query:

```bash
docker exec guardian-postgres psql -U guardian_user -d guardian_plants \
  -c "select id, device_id, conversation_id, role, left(content, 100) as content_preview from chat_histories order by id desc limit 20;"
```

## Current Debug Path

Prefer request flow tracing first:

```bash
curl http://localhost/api/logs/flow
```

Then inspect a specific trace:

```bash
curl http://localhost/api/logs/flow/<TRACE_ID>
```

Expected chat steps:

```text
received -> ai_call -> ai_response -> db_saved -> complete
```

If the assistant message is missing, check:

```bash
docker logs guardian-server --tail 300 | grep -i "trace\|chat\|ai_response\|db_saved\|error"
```

## Raw AI Provider Check

Use only on the VPS or another trusted environment where `AI_API_KEY` is set:

```bash
curl -s -H "Authorization: Bearer $AI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"gemma4:31b-cloud","stream":true,"messages":[{"role":"user","content":"hello"}]}' \
  "$AI_BASE_URL/chat/completions" | head -20
```

Compare the actual streaming shape with what `ChatService.java` expects.

## Likely Fix Areas

- `ChatService.java` SSE parsing of OpenAI-compatible streaming chunks.
- `ChatHistoryRepository.java` skip logic for blank assistant content.
- AI provider response format or model-specific stream behavior.
- Network interruption before the stream completion callback.
