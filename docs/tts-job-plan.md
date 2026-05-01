# TTS Job Plan

Current implementation:

```text
Android -> POST /api/tts/synthesize -> Spring Boot -> VoiceVOX -> WAV response
```

This is synchronous. It is simple and works for early testing, but VoiceVOX can
be slow and CPU-heavy.

## When To Change

Move to asynchronous jobs if any of these become common:

- Android waits too long for TTS responses.
- Multiple users share one VPS.
- VoiceVOX CPU usage causes chat latency.
- Audio generation needs retry or queue visibility.

## Target Shape

```text
POST /api/tts/jobs
  -> create tts_jobs row
  -> return jobId

worker
  -> call VoiceVOX
  -> store WAV file or object key
  -> mark job complete/error

GET /api/tts/jobs/{jobId}
  -> pending/running/complete/error

GET /api/tts/jobs/{jobId}/audio
  -> WAV when ready
```

Possible table:

```text
tts_jobs
  id
  device_id
  text
  speaker
  status
  audio_path
  error_message
  created_at
  completed_at
```

Keep synchronous TTS until real latency or load requires this.
