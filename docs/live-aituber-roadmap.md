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

The live stage can currently show an internal object-like string at the top
left, for example:

```text
{type=text, text=自動雑談です。ユーザーにはこの指示文を見せず...
```

Likely cause:

```text
LiveStateService currently derives latestUserText from ServerMessage content
using contentAsString(), which can stringify structured content arrays/objects.
Some auto-talk/internal prompt text is also being treated as display text.
```

Recommended fix:

```text
Separate internal prompt text from user-visible display text.
Extract only plain user text for the stage.
Hide auto-talk/system instructions from /api/live/state.
Consider fields such as userText, displayUserText, assistantText, internalPrompt.
```

## Next Implementation Strategy

Recommended priority order:

1. Fix live state display text.

   Make the stage safe for public display before adding more effects. Internal
   prompts must never appear in OBS/browser output.

2. Finish AP server managed prompt control.

   Android should remain a capture/input client in server mode. The AP server
   should own guardian personality, auto-talk rules, history summary, image
   diagnostic decisions, and safety text.

3. Improve the OBS stage UI.

   Target 16:9 browser source first. Add stable safe areas, captions, plant
   image framing, guardian status, and a layout that works at 1920x1080.

4. Add server-generated audio to the stage.

   Server should create VoiceVOX/AAC audio and expose `audioUrl` in live state.
   The browser stage needs an audio enable button because browser autoplay can
   block sound until user interaction.

5. Add guardian visuals in stages.

   Start with 2D expressions before full 3D. Suggested states:

   ```text
   idle
   thinking
   speaking
   happy
   worried
   error
   ```

   Full 3D or VRM can be added later after the live flow is stable.

6. Add a live control page.

   Candidate URL:

   ```text
   /admin/live.html
   ```

   Useful controls:

   ```text
   live ON/OFF
   auto-talk ON/OFF
   auto-talk interval
   current image preview
   manual speak textbox
   active AI profile selector
   TTS speaker selector
   stage reset button
   ```

7. Split AI roles by purpose.

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

8. Add admin security.

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
