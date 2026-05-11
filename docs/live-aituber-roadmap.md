# Live AItuber Roadmap and Handoff

Last updated: 2026-05-11

This memo records the current live/AItuber direction, the active server setup,
and the next implementation priorities. It is written so another PC can resume
work without needing the original chat history.

## Servers and Repositories

```text
AP server / app server: 80.241.214.154
Gitea server:          158.220.99.197
Gitea web:             http://158.220.99.197:3000
Gitea SSH port:        2222
Repository:            Guardian_Of_The_Plants/GuardianOfThePlants.git
AP server repo path:   ~/guardian_of_the_plants
```

The AP server should pull from Gitea:

```bash
cd ~/guardian_of_the_plants
git remote -v
git pull origin main
```

If the AP server remote needs to be corrected:

```bash
cd ~/guardian_of_the_plants
git remote set-url origin ssh://git@158.220.99.197:2222/Guardian_Of_The_Plants/GuardianOfThePlants.git
git ls-remote origin
```

## Current System Shape

```text
Android app
  -> nginx on AP server
    -> Spring Boot server container
      -> PostgreSQL container
      -> VoiceVOX container
      -> external AI provider, or host Ollama through host.docker.internal
  -> browser admin pages
  -> browser live stage for OBS capture
```

Main public pages on the AP server:

```text
http://80.241.214.154/admin/logs.html
http://80.241.214.154/admin/ai.html
http://80.241.214.154/admin/live.html
http://80.241.214.154/live/stage.html
```

Important API endpoints:

```text
GET  /api/health
GET  /api/ai/profiles
POST /api/ai/active
POST /api/ai/test
POST /api/chat
POST /api/live/message
GET  /api/live/state
GET  /api/live/audio/{id}
POST /api/tts/synthesize
GET  /api/logs/flow
DELETE /api/logs
```

## Implemented Live Features

The first live foundation is already in place.

```text
Android server chat can send to POST /api/live/message.
The server keeps latest live state in memory.
/live/stage.html polls /api/live/state and displays plant image, guardian area,
current text, status, and audio URL when present.
nginx proxies /live/ to the Spring Boot server.
/live/stage.html includes a temporary Three.js guardian and an Enable Audio
button for browser autoplay restrictions.
/admin/live.html can send manual live messages and preview the stage.
/live/stage.html now loads the Android angel model `angel_egna.glb` through
Three.js GLTFLoader from `/live/assets/models/angel_egna.glb`.
/admin/live.html supports PC text chat, Web Speech API voice input, and quick
choice buttons similar to the smartphone shortcuts.
/admin/live.html can attach a local plant image file to the next PC chat
message. The browser converts it to a data URL and sends it through the same
image part format used by the smartphone flow.
/admin/live.html also has an Auto topic toggle. While the admin page is open,
it can periodically send a short prompt so the guardian starts a topic by
itself.
/admin/live.html has a 3D Pose selector backed by `/api/live/settings`.
The selected preset is shared with `/live/stage.html`, including OBS or another
browser tab.
/admin/live.html also includes the AI profile selector and connection test that
used to live on `/admin/ai.html`, so live operation can switch models from one
page. Built-in Ollama Cloud profiles include `gemma4:31b-cloud` and
`deepseek-v4-flash`.
/live/stage.html reports browser-side 3D initialization and GLB load failures
to `POST /api/logs` with category `LiveStage3D`, so they appear in
`/admin/logs.html`. The App Logs table also shows a short `details` preview for
browser error messages, user agent, and model URL.
/live/stage.html normalizes the GLB to the ground, plays an idle animation if
the GLB has one, lowers arm bones as a fallback, and drives mouth morphs from
the actual audio waveform instead of a fixed timer.
For non-raw pose presets, the stage disables GLB animation mixer playback and
applies stronger arm-bone rotations so pose changes are visibly testable.
```

The live stage is intended for OBS browser-source capture. It is not yet the
final streaming system; it is the base for plant image + guardian + captions.

## AI Profile Switching

The server supports multiple AI profiles and can switch the active AI from the
browser page:

```text
/admin/ai.html
```

The page must show:

```text
current selected profile label
current selected model name
profile list
connection test button
switch button
```

API keys are intentionally masked and must not be shown in the browser.

Current AP server profile example:

```env
AI_ACTIVE_PROFILE=cloud-default
AI_PROFILES_JSON=[{"id":"cloud-default","label":"Cloud default","baseUrl":"https://ollama.com/v1","apiKey":"<cloud api key>","model":"gemma4:31b-cloud"},{"id":"ollama-gemma4-e2b","label":"AP Ollama gemma4:e2b","baseUrl":"http://host.docker.internal:11434/v1","apiKey":"ollama","model":"gemma4:e2b"}]
```

The AP server has host Ollama running at:

```text
http://localhost:11434/
```

The server container reaches it through:

```text
http://host.docker.internal:11434/v1
```

Confirmed model:

```text
gemma4:e2b
```

Confirm from the AP server:

```bash
curl http://localhost:11434/v1/models
docker exec guardian-server curl -s http://host.docker.internal:11434/v1/models
curl -s http://localhost/api/ai/profiles
curl -s -X POST http://localhost/api/ai/test \
  -H "Content-Type: application/json" \
  -d '{"profileId":"ollama-gemma4-e2b"}'
```

## Android AP Server Control Mode

Android settings now include an AP server control mode near the top of the
settings screen.

Purpose:

```text
When ProviderType.SERVER is selected and AP server control mode is ON,
Android sends only the user's current text and selected images.
Android does not add guardian prompts, history prompts, knowledge prompts, or
image diagnostic prompts.
The AP server becomes responsible for prompt/personality control.
```

This mode is important for the live/AItuber design because prompt control
should be centralized on the AP server.

## Current Known Issue

The live stage previously could show an internal object-like string at the top
left, for example:

```text
{type=text, text=自動雑談です。ユーザーにはこの指示文を見せず...
```

Cause:

```text
LiveStateService derived latestUserText from ServerMessage content using
contentAsString(), which can stringify structured content arrays/objects.
Some auto-talk/internal prompt text was also being treated as display text.
```

Current mitigation:

```text
LiveStateService extracts only plain text parts for display.
Internal auto-talk instructions are replaced with "自動トーク" in live state.
LiveController ignores client system prompts for live messages and injects the
AP-server-managed guardian prompt.
/live/stage.html has been rebuilt as a 16:9 OBS-friendly display.
```

## Next Implementation Strategy

Recommended priority order:

1. Improve live control operations.

   Add live ON/OFF, auto-talk interval, TTS speaker selection, image preview,
   and reset controls to `/admin/live.html`.

2. Tune the guardian model for OBS.

   The stage now uses `angel_egna.glb`, but camera, scale, lighting, animation,
   and mouth/face morph handling should be tuned through real OBS testing.
   Suggested states:

   ```text
   idle
   thinking
   speaking
   happy
   worried
   error
   ```

   Full 3D or VRM can be added later after the live flow is stable.

3. Split AI roles by purpose.

   The current active AI is global. For production live use, separate profiles
   may be better:

   ```text
   chat AI
   image diagnostic AI
   live talk AI
   summary/memory AI
   safety/check AI
   ```

   Note: local `gemma4:e2b` is useful for low-cost text, but image diagnosis
   may need a vision-capable model.

4. Add admin security.

   The admin pages are currently convenient but should be protected before
   wider use:

   ```text
   /admin/logs.html
   /admin/ai.html
   future /admin/live.html
   ```

   Minimum recommended controls are Basic Auth or token auth, HTTPS, rate
   limits, and confirmation for destructive actions.

## AP Server Update Commands

Normal update:

```bash
cd ~/guardian_of_the_plants
git pull origin main
docker compose build server
docker compose up -d server nginx
docker compose ps
curl -s http://localhost/api/health
curl -I http://localhost/live/stage.html
```

If nginx config changed and the old response remains cached inside the running
container, restart nginx:

```bash
docker compose restart nginx
curl -I http://localhost/live/stage.html
```

Check AI profile state:

```bash
curl -s http://localhost/api/ai/profiles
```

Check logs:

```bash
docker logs guardian-server --tail 100
docker compose ps
```

## Do Not Commit

These files/directories are local or secret and should not be committed:

```text
.env
android/local.properties
.tools/
server/build/
android/.gradle/
android/app/build/
```

Use placeholders in docs. Never paste real API keys.
