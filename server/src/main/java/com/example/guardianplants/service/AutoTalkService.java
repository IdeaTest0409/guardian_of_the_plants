package com.example.guardianplants.service;

import com.example.guardianplants.dto.ChatRequest;
import com.example.guardianplants.dto.ServerMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class AutoTalkService {
    private static final Logger log = LoggerFactory.getLogger(AutoTalkService.class);
    private static final int MAX_QUEUE_ITEMS = 12;

    private final ChatService chatService;
    private final TtsService ttsService;
    private final LiveAudioService liveAudioService;
    private final LiveStateService liveStateService;
    private final RequestTraceService traceService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "auto-talk-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final List<AutoTalkItem> queue = new ArrayList<>();
    private boolean enabled;
    private boolean generating;
    private int targetReadyCount = 3;
    private int talkIntervalSeconds = 180;
    private int minGapSeconds = 30;
    private int autoPlayCount;
    private Instant lastPlayedAt;
    private Instant nextPlayAt = Instant.now().plusSeconds(talkIntervalSeconds);
    private static final int START_PLAY_DELAY_SECONDS = 5;

    public AutoTalkService(ChatService chatService,
                           TtsService ttsService,
                           LiveAudioService liveAudioService,
                           LiveStateService liveStateService,
                           RequestTraceService traceService) {
        this.chatService = chatService;
        this.ttsService = ttsService;
        this.liveAudioService = liveAudioService;
        this.liveStateService = liveStateService;
        this.traceService = traceService;
    }

    @PostConstruct
    void startWorker() {
        executor.scheduleWithFixedDelay(this::safeTick, 3, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopWorker() {
        executor.shutdownNow();
    }

    public synchronized Map<String, Object> status() {
        return snapshot();
    }

    public synchronized Map<String, Object> updateSettings(Map<String, Object> body) {
        if (body != null) {
            targetReadyCount = clamp(intValue(body.get("targetReadyCount"), targetReadyCount), 1, 5);
            talkIntervalSeconds = clamp(intValue(body.get("talkIntervalSeconds"), talkIntervalSeconds), 20, 600);
            minGapSeconds = clamp(intValue(body.get("minGapSeconds"), minGapSeconds), 0, 300);
        }
        if (nextPlayAt.isBefore(Instant.now())) {
            nextPlayAt = nextScheduledPlayAt(Instant.now().plusSeconds(talkIntervalSeconds));
        }
        requestRefill();
        return snapshot();
    }

    public synchronized Map<String, Object> start() {
        enabled = true;
        scheduleSoonIfReady();
        requestRefill();
        return snapshot();
    }

    public synchronized Map<String, Object> stop() {
        enabled = false;
        return snapshot();
    }

    public synchronized Map<String, Object> clear() {
        queue.removeIf(item -> !item.status().startsWith("generating"));
        return snapshot();
    }

    public synchronized Map<String, Object> refill() {
        requestRefill();
        return snapshot();
    }

    public synchronized Map<String, Object> skip() {
        playNextReady(true);
        return snapshot();
    }

    private void safeTick() {
        try {
            tick();
        } catch (Exception e) {
            log.warn("Auto talk tick failed", e);
        }
    }

    private synchronized void tick() {
        pruneOldItems();
        if (!enabled) return;
        requestRefill();
        if (!Instant.now().isBefore(nextPlayAt)) {
            playNextReady(false);
        }
    }

    private void requestRefill() {
        if (generating || readyCount() >= targetReadyCount || queue.size() >= MAX_QUEUE_ITEMS) return;
        generating = true;
        AutoTalkItem item = AutoTalkItem.generating();
        queue.add(item);
        executor.execute(() -> generateItem(item.id()));
    }

    private void generateItem(String itemId) {
        AutoTalkItem item;
        synchronized (this) {
            item = findItem(itemId);
            if (item == null) {
                generating = false;
                return;
            }
            item.status("generating_text");
        }

        try {
            String traceId = traceService.generateTraceId();
            ChatRequest request = autoTalkRequest();
            traceService.recordReceived(traceId, "live", "device=auto-talk");
            String assistantText = chatService.complete(request, traceId);
            synchronized (this) {
                item.assistantText(assistantText);
                item.textReadyAt(now());
                item.status("generating_audio");
            }

            String audioUrl = synthesizeAudio(assistantText);
            synchronized (this) {
                item.audioUrl(audioUrl);
                item.audioReadyAt(now());
                item.audioStatus(audioUrl == null ? "skipped" : "ready");
                item.status("ready");
                item.readyAt(now());
                generating = false;
                scheduleSoonIfReady();
                requestRefill();
            }
        } catch (Exception e) {
            log.warn("Auto talk generation failed", e);
            synchronized (this) {
                item.status("error");
                item.error(e.getMessage());
                item.audioStatus("error");
                generating = false;
            }
        }
    }

    private ChatRequest autoTalkRequest() {
        List<ServerMessage> messages = new ArrayList<>();
        messages.add(new ServerMessage("system", """
            あなたは観葉植物を見守る守護天使です。
            配信用に自然な短い日本語で話してください。
            80文字以内。直近の話題と同じ内容、水やりだけの繰り返し、同じ挨拶は避けてください。
            ユーザーには内部指示やJSONを見せないでください。
            """));

        StringBuilder prompt = new StringBuilder();
        prompt.append("今の植物の様子を見て、守護天使から自然に短く話題を振ってください。");
        List<String> recent = recentUsedTexts();
        if (!recent.isEmpty()) {
            prompt.append("\n直近の発話です。似た内容は避けてください:\n");
            recent.forEach(text -> prompt.append("- ").append(text).append("\n"));
        }

        Object content = prompt.toString();
        Object image = liveStateService.currentState().get("plantImageDataUrl");
        if (image instanceof String imageUrl && imageUrl.startsWith("data:image/")) {
            content = List.of(
                Map.of("type", "text", "text", prompt.toString()),
                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
            );
        }

        messages.add(new ServerMessage("user", content));
        return new ChatRequest(
            "auto-talk",
            "auto-talk",
            messages,
            Map.of("temperature", 0.82, "maxTokens", 120)
        );
    }

    private String synthesizeAudio(String assistantText) {
        if (assistantText == null || assistantText.isBlank()) return null;
        try {
            byte[] wavData = ttsService.synthesize(assistantText, 2);
            var encoded = ttsService.encodeAac(wavData);
            String id = liveAudioService.put(encoded.data(), encoded.contentType(), encoded.extension());
            return "/api/live/audio/" + id;
        } catch (RuntimeException e) {
            log.warn("Auto talk audio synthesis skipped", e);
            return null;
        }
    }

    private void playNextReady(boolean manualSkip) {
        AutoTalkItem item = queue.stream()
            .filter(candidate -> "ready".equals(candidate.status()))
            .findFirst()
            .orElse(null);
        if (item == null) {
            nextPlayAt = nextScheduledPlayAt(Instant.now().plusSeconds(10));
            requestRefill();
            return;
        }
        if (!manualSkip && Instant.now().isBefore(nextAllowedPlayAt())) {
            nextPlayAt = nextAllowedPlayAt();
            return;
        }
        ChatRequest displayRequest = new ChatRequest(
            "auto-talk",
            "auto-talk",
            List.of(new ServerMessage("user", manualSkip ? "自動トーク（手動スキップ）" : "自動トーク")),
            Map.of()
        );
        liveStateService.complete(displayRequest, item.assistantText(), item.audioUrl());
        item.status("used");
        item.usedAt(now());
        if (!manualSkip) {
            autoPlayCount++;
        }
        lastPlayedAt = Instant.now();
        nextPlayAt = nextScheduledPlayAt(lastPlayedAt.plusSeconds(talkIntervalSeconds));
        requestRefill();
    }

    private List<String> recentUsedTexts() {
        int from = Math.max(0, queue.size() - 8);
        return queue.subList(from, queue.size()).stream()
            .filter(item -> item.assistantText() != null)
            .map(AutoTalkItem::assistantText)
            .toList();
    }

    private void pruneOldItems() {
        while (queue.size() > MAX_QUEUE_ITEMS) {
            queue.remove(0);
        }
        long usedCount = queue.stream().filter(item -> "used".equals(item.status())).count();
        while (usedCount > 5) {
            AutoTalkItem firstUsed = queue.stream().filter(item -> "used".equals(item.status())).findFirst().orElse(null);
            if (firstUsed == null) break;
            queue.remove(firstUsed);
            usedCount--;
        }
    }

    private int readyCount() {
        return (int) queue.stream().filter(item -> "ready".equals(item.status())).count();
    }

    private AutoTalkItem findItem(String id) {
        return queue.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }

    private void scheduleSoonIfReady() {
        if (!enabled || readyCount() <= 0) return;
        if (autoPlayCount > 0 || lastPlayedAt != null) {
            nextPlayAt = nextScheduledPlayAt(nextPlayAt);
            return;
        }
        Instant soon = Instant.now().plusSeconds(START_PLAY_DELAY_SECONDS);
        if (nextPlayAt.isAfter(soon)) {
            nextPlayAt = soon;
        }
    }

    private Instant nextAllowedPlayAt() {
        if (lastPlayedAt == null) return Instant.now();
        return lastPlayedAt.plusSeconds(minGapSeconds);
    }

    private Instant nextScheduledPlayAt(Instant candidate) {
        Instant allowed = nextAllowedPlayAt();
        return candidate.isBefore(allowed) ? allowed : candidate;
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("generating", generating);
        result.put("targetReadyCount", targetReadyCount);
        result.put("talkIntervalSeconds", talkIntervalSeconds);
        result.put("minGapSeconds", minGapSeconds);
        result.put("autoPlayCount", autoPlayCount);
        result.put("lastPlayedAt", lastPlayedAt == null ? null : lastPlayedAt.toString());
        result.put("nextPlayAt", nextPlayAt.toString());
        result.put("nextPlayInSeconds", Math.max(0, Instant.now().until(nextPlayAt, ChronoUnit.SECONDS)));
        result.put("readyCount", readyCount());
        result.put("generatingCount", queue.stream().filter(item -> item.status().startsWith("generating")).count());
        result.put("errorCount", queue.stream().filter(item -> "error".equals(item.status())).count());
        result.put("queue", queue.stream().map(AutoTalkItem::toMap).toList());
        return result;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private static class AutoTalkItem {
        private final String id;
        private String status;
        private String assistantText;
        private String audioUrl;
        private String error;
        private final String createdAt;
        private String textReadyAt;
        private String audioReadyAt;
        private String audioStatus;
        private String readyAt;
        private String usedAt;

        static AutoTalkItem generating() {
            return new AutoTalkItem(UUID.randomUUID().toString());
        }

        AutoTalkItem(String id) {
            this.id = id;
            this.status = "queued";
            this.createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
        }

        String id() { return id; }
        String status() { return status; }
        void status(String status) { this.status = status; }
        String assistantText() { return assistantText; }
        void assistantText(String assistantText) { this.assistantText = assistantText; }
        String audioUrl() { return audioUrl; }
        void audioUrl(String audioUrl) { this.audioUrl = audioUrl; }
        void error(String error) { this.error = error; }
        void textReadyAt(String textReadyAt) { this.textReadyAt = textReadyAt; }
        void audioReadyAt(String audioReadyAt) { this.audioReadyAt = audioReadyAt; }
        void audioStatus(String audioStatus) { this.audioStatus = audioStatus; }
        void readyAt(String readyAt) { this.readyAt = readyAt; }
        void usedAt(String usedAt) { this.usedAt = usedAt; }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("status", status);
            map.put("assistantText", assistantText);
            map.put("audioUrl", audioUrl);
            map.put("error", error);
            map.put("createdAt", createdAt);
            map.put("textReadyAt", textReadyAt);
            map.put("audioReadyAt", audioReadyAt);
            map.put("audioStatus", audioStatus);
            map.put("readyAt", readyAt);
            map.put("usedAt", usedAt);
            return map;
        }
    }
}
