# Assistant Message DB Save Debugging Plan

Created: 2026-04-30

## Problem

`chat_histories` table shows `user` role rows but **no `assistant` role rows**.

Verified via VPS PostgreSQL:

```bash
docker exec guardian-postgres psql -U guardian_user -d guardian_plants \
  -c "SELECT id, device_id, conversation_id, role, left(content, 100) as content_preview FROM chat_histories ORDER BY id DESC LIMIT 20;"
```

Result: 8 rows, all `role = user`. Zero `assistant` rows.

Server logs (`docker logs guardian-server`) show **no SSE parse errors** or `Non-JSON SSE chunk` warnings.

## Root Cause Hypothesis

The SSE stream parsing logic in `ChatService.java` has three save paths:

1. `[DONE]` token received (line 86-93)
2. `finish_reason` detected in JSON (line 106-109)
3. Stream completion callback (line 134-135)

The `insert()` method in `ChatHistoryRepository.java` skips empty content:

```java
if (content == null || content.isBlank()) {
    return;  // silently skipped
}
```

**Likely cause**: `accumulatedContent` is empty at save time because either:
- The SSE chunks from Ollama Cloud don't contain parsable `delta.content` fields (format mismatch)
- JSON parsing silently fails in the `catch` block (log level is `DEBUG`, so errors are invisible at default log level)
- None of the three save paths are triggered (stream ends without `[DONE]` or `finish_reason`)

## Debug Steps

### Step 1: Add diagnostic logging

Add `log.info` statements before each `chatHistoryRepository.insert()` call to log `accumulatedContent.length()`:

```java
log.info("Saving assistant message on [DONE]: content length={}", accumulatedContent.length());
log.info("Saving assistant message on finish_reason: content length={}", accumulatedContent.length());
log.info("Saving assistant message on stream complete: content length={}", accumulatedContent.length());
```

Also add debug logging for each SSE chunk:

```java
log.debug("SSE data chunk: {}", data);
```

### Step 2: Raise the catch block log level

Change `log.debug("Non-JSON SSE chunk: {}", chunk)` to `log.warn` to make parse failures visible:

```java
log.warn("SSE parse error (chunk skipped for DB save): {}", chunk, e);
```

### Step 3: Rebuild and deploy on VPS

```bash
./gradlew :server:bootJar
docker compose up -d --build server
```

### Step 4: Trigger a chat request and check logs

```bash
docker logs guardian-server --tail 200 | grep -i "SSE\|Saving assistant\|content length\|parse error"
```

Expected outcomes:
- If `content length=0` appears → `accumulatedContent` never received content (SSE format mismatch)
- If `SSE parse error` appears → JSON structure differs from expected OpenAI format
- If nothing appears → save paths are never reached (stream lifecycle issue)

### Step 5: Check raw SSE format from Ollama Cloud

If logging doesn't reveal the cause, capture raw SSE output:

```bash
curl -s -H "Authorization: Bearer $AI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"gemma4:31b-cloud","stream":true,"messages":[{"role":"user","content":"こんにちは"}]}' \
  $AI_BASE_URL/chat/completions | head -20
```

Compare the actual JSON structure with what `ChatService.java` expects (`choices[0].delta.content`).

## Fix Plan (after diagnosis)

Depending on findings:
- If `delta.content` path is wrong: update JSON navigation
- If `finish_reason` is in a different field: update the detection logic
- If stream completes without explicit signals: rely on the `onCompletion` callback
- If content is never accumulated: verify the SSE chunk format matches OpenAI-compatible streaming
