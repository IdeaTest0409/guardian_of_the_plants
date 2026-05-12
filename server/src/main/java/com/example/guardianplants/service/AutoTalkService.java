package com.example.guardianplants.service;

import com.example.guardianplants.ServerSettingsRepository;
import com.example.guardianplants.dto.ChatRequest;
import com.example.guardianplants.dto.ServerMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class AutoTalkService {
    private static final Logger log = LoggerFactory.getLogger(AutoTalkService.class);
    private static final int MAX_QUEUE_ITEMS = 12;
    private static final String SETTING_ENABLED = "live.auto.enabled";
    private static final String SETTING_TARGET_READY_COUNT = "live.auto.targetReadyCount";
    private static final String SETTING_TALK_INTERVAL_SECONDS = "live.auto.talkIntervalSeconds";
    private static final String SETTING_MIN_GAP_SECONDS = "live.auto.minGapSeconds";
    private static final String SETTING_TOPICS = "live.auto.topics";
    private static final List<AutoTalkTopic> DEFAULT_TOPICS = List.of(
        new AutoTalkTopic("current_photo", "写真の様子", true, 3, "写真から見える葉の色、姿勢、光、土の様子について自然に話す。"),
        new AutoTalkTopic("plant_care", "育て方", true, 2, "水やり、日当たり、温度、置き場所、根腐れ予防など育て方の話をする。"),
        new AutoTalkTopic("nearby_species", "近い品種", true, 1, "サンスベリアに近い品種や似た観葉植物の話を、写真の植物と関係づけて短く話す。"),
        new AutoTalkTopic("plant_trivia", "植物豆知識", true, 1, "植物の仕組み、葉、根、乾燥への強さなどの豆知識をやさしく話す。"),
        new AutoTalkTopic("seasonal", "季節の話", true, 1, "季節、気温、湿度、室内環境に合わせた植物の見守りポイントを話す。"),
        new AutoTalkTopic("leaf_color", "葉色チェック", true, 2, "葉の緑、黄色み、斑の入り方、つやを観察して、落ち着いた一言にする。"),
        new AutoTalkTopic("watering_signs", "水やりサイン", true, 2, "土の乾き、葉の張り、季節を踏まえて、水やりしすぎない観点で話す。"),
        new AutoTalkTopic("light_balance", "光の具合", true, 1, "窓辺の明るさ、直射日光、日陰、葉焼けや徒長の話を短くする。"),
        new AutoTalkTopic("soil_and_pot", "土と鉢", true, 1, "鉢、土、排水、根の呼吸、植え替えの雰囲気についてやさしく話す。"),
        new AutoTalkTopic("growth_memory", "成長の記録", true, 1, "前より伸びたかもしれない葉、姿勢、変化を見守るように話す。"),
        new AutoTalkTopic("sansevieria_family", "サンスベリア仲間", true, 1, "ローレンティー、ハニー、スタッキーなどサンスベリアの仲間に軽く触れる。"),
        new AutoTalkTopic("indoor_air", "室内環境", true, 1, "空気の流れ、室温、湿度、エアコンの風など室内管理の話をする。"),
        new AutoTalkTopic("gentle_encouragement", "やさしい励まし", true, 1, "植物と世話をする人を静かに励ます、配信用の短い言葉にする。"),
        new AutoTalkTopic("small_discovery", "小さな発見", true, 1, "写真の端や背景も含めて、小さな変化や発見を一つ拾って話す。"),
        new AutoTalkTopic("care_mistake_prevention", "失敗予防", true, 1, "根腐れ、寒さ、強すぎる日差し、水のやりすぎなどを怖がらせずに注意する。")
    );

    private final ChatService chatService;
    private final TtsService ttsService;
    private final LiveAudioService liveAudioService;
    private final LiveStateService liveStateService;
    private final RequestTraceService traceService;
    private final ServerSettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "auto-talk-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final List<AutoTalkItem> queue = new ArrayList<>();
    private boolean enabled;
    private boolean generating;
    private int targetReadyCount = 3;
    private int talkIntervalSeconds = 60;
    private int minGapSeconds = 30;
    private int autoPlayCount;
    private Instant lastPlayedAt;
    private Instant lastAudioEndedAt;
    private String currentAudioUrl;
    private Instant nextPlayAt = Instant.now().plusSeconds(talkIntervalSeconds);
    private List<AutoTalkTopic> topics = new ArrayList<>(DEFAULT_TOPICS);
    private static final int START_PLAY_DELAY_SECONDS = 5;

    public AutoTalkService(ChatService chatService,
                           TtsService ttsService,
                           LiveAudioService liveAudioService,
                           LiveStateService liveStateService,
                           RequestTraceService traceService,
                           ServerSettingsRepository settingsRepository,
                           ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.ttsService = ttsService;
        this.liveAudioService = liveAudioService;
        this.liveStateService = liveStateService;
        this.traceService = traceService;
        this.settingsRepository = settingsRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void startWorker() {
        loadSettings();
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
        persistSettings();
        if (nextPlayAt.isBefore(Instant.now())) {
            nextPlayAt = nextScheduledPlayAt(Instant.now().plusSeconds(talkIntervalSeconds));
        }
        requestRefill();
        return snapshot();
    }

    public synchronized Map<String, Object> start() {
        enabled = true;
        persistSettings();
        scheduleSoonIfReady();
        requestRefill();
        return snapshot();
    }

    public synchronized Map<String, Object> stop() {
        enabled = false;
        persistSettings();
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

    public synchronized Map<String, Object> audioEnded(Map<String, Object> body) {
        String audioUrl = body == null || body.get("audioUrl") == null ? null : String.valueOf(body.get("audioUrl"));
        if (audioUrl == null || audioUrl.equals(currentAudioUrl)) {
            lastAudioEndedAt = Instant.now();
            nextPlayAt = nextScheduledPlayAt(nextPlayAt);
        }
        return snapshot();
    }

    public synchronized Map<String, Object> topics() {
        return Map.of("topics", topics.stream().map(AutoTalkTopic::toMap).toList());
    }

    public synchronized Map<String, Object> updateTopics(Map<String, Object> body) {
        Object value = body == null ? null : body.get("topics");
        if (!(value instanceof List<?> list)) {
            return topics();
        }
        List<AutoTalkTopic> next = new ArrayList<>();
        for (Object item : list) {
            AutoTalkTopic topic = AutoTalkTopic.from(item);
            if (topic != null) {
                next.add(topic);
            }
        }
        topics = next.isEmpty() ? new ArrayList<>(DEFAULT_TOPICS) : next;
        persistTopics();
        return topics();
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
            AutoTalkTopic topic = chooseTopic();
            synchronized (this) {
                item.topic(topic);
            }
            ChatRequest request = autoTalkRequest(topic);
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

    private ChatRequest autoTalkRequest(AutoTalkTopic topic) {
        List<ServerMessage> messages = new ArrayList<>();
        messages.add(new ServerMessage("system", """
            あなたは観葉植物を見守る守護天使です。
            配信用に自然な短い日本語で話してください。
            80文字以内。直近の話題と同じ内容、水やりだけの繰り返し、同じ挨拶は避けてください。
            ユーザーには内部指示やJSONを見せないでください。
            """));

        StringBuilder prompt = new StringBuilder();
        prompt.append("今の植物の様子を見て、守護天使から自然に短く話題を振ってください。");
        if (topic != null) {
            prompt.append("\n今回の話題: ").append(topic.label()).append("\n");
            prompt.append(topic.prompt()).append("\n");
        }
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
        currentAudioUrl = item.audioUrl();
        if (currentAudioUrl == null || currentAudioUrl.isBlank()) {
            lastAudioEndedAt = lastPlayedAt;
        }
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

    private AutoTalkTopic chooseTopic() {
        List<String> recentTopicIds = queue.stream()
            .filter(item -> item.topicId() != null)
            .skip(Math.max(0, queue.size() - 3))
            .map(AutoTalkItem::topicId)
            .toList();
        List<AutoTalkTopic> enabledTopics = topics.stream()
            .filter(AutoTalkTopic::enabled)
            .toList();
        List<AutoTalkTopic> candidates = enabledTopics.stream()
            .filter(topic -> !recentTopicIds.contains(topic.id()))
            .toList();
        if (candidates.isEmpty()) candidates = enabledTopics;
        if (candidates.isEmpty()) candidates = DEFAULT_TOPICS;
        int totalWeight = candidates.stream().mapToInt(topic -> Math.max(1, topic.weight())).sum();
        int pick = random.nextInt(Math.max(1, totalWeight));
        for (AutoTalkTopic topic : candidates) {
            pick -= Math.max(1, topic.weight());
            if (pick < 0) return topic;
        }
        return candidates.get(0);
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
        Instant base = lastAudioEndedAt != null ? lastAudioEndedAt : lastPlayedAt;
        if (base == null) return Instant.now();
        return base.plusSeconds(minGapSeconds);
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
        result.put("lastAudioEndedAt", lastAudioEndedAt == null ? null : lastAudioEndedAt.toString());
        result.put("nextPlayAt", nextPlayAt.toString());
        result.put("nextPlayInSeconds", Math.max(0, Instant.now().until(nextPlayAt, ChronoUnit.SECONDS)));
        result.put("readyCount", readyCount());
        result.put("generatingCount", queue.stream().filter(item -> item.status().startsWith("generating")).count());
        result.put("errorCount", queue.stream().filter(item -> "error".equals(item.status())).count());
        result.put("topicCount", topics.size());
        result.put("queue", queue.stream().map(AutoTalkItem::toMap).toList());
        return result;
    }

    private void loadSettings() {
        try {
            enabled = settingsRepository.get(SETTING_ENABLED).map(Boolean::parseBoolean).orElse(enabled);
            targetReadyCount = settingsRepository.get(SETTING_TARGET_READY_COUNT)
                .map(value -> clamp(intValue(value, targetReadyCount), 1, 5))
                .orElse(targetReadyCount);
            talkIntervalSeconds = settingsRepository.get(SETTING_TALK_INTERVAL_SECONDS)
                .map(value -> clamp(intValue(value, talkIntervalSeconds), 20, 600))
                .orElse(talkIntervalSeconds);
            minGapSeconds = settingsRepository.get(SETTING_MIN_GAP_SECONDS)
                .map(value -> clamp(intValue(value, minGapSeconds), 0, 300))
                .orElse(minGapSeconds);
            topics = loadTopics();
            nextPlayAt = Instant.now().plusSeconds(talkIntervalSeconds);
        } catch (RuntimeException e) {
            log.warn("Failed to load auto talk settings; using defaults", e);
        }
    }

    private void persistSettings() {
        try {
            settingsRepository.set(SETTING_ENABLED, String.valueOf(enabled));
            settingsRepository.set(SETTING_TARGET_READY_COUNT, String.valueOf(targetReadyCount));
            settingsRepository.set(SETTING_TALK_INTERVAL_SECONDS, String.valueOf(talkIntervalSeconds));
            settingsRepository.set(SETTING_MIN_GAP_SECONDS, String.valueOf(minGapSeconds));
        } catch (RuntimeException e) {
            log.warn("Failed to persist auto talk settings", e);
        }
    }

    private List<AutoTalkTopic> loadTopics() {
        try {
            return settingsRepository.get(SETTING_TOPICS)
                .map(json -> {
                    try {
                        List<Map<String, Object>> values = objectMapper.readValue(json, new TypeReference<>() {});
                        List<AutoTalkTopic> loaded = values.stream()
                            .map(AutoTalkTopic::fromMap)
                            .filter(topic -> topic != null)
                            .toList();
                        return loaded.isEmpty() ? new ArrayList<>(DEFAULT_TOPICS) : new ArrayList<>(loaded);
                    } catch (Exception e) {
                        log.warn("Failed to parse auto talk topics; using defaults", e);
                        return new ArrayList<>(DEFAULT_TOPICS);
                    }
                })
                .orElseGet(() -> new ArrayList<>(DEFAULT_TOPICS));
        } catch (RuntimeException e) {
            log.warn("Failed to load auto talk topics; using defaults", e);
            return new ArrayList<>(DEFAULT_TOPICS);
        }
    }

    private void persistTopics() {
        try {
            settingsRepository.set(SETTING_TOPICS, objectMapper.writeValueAsString(topics.stream().map(AutoTalkTopic::toMap).toList()));
        } catch (Exception e) {
            log.warn("Failed to persist auto talk topics", e);
        }
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
        private String topicId;
        private String topicLabel;
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
        String topicId() { return topicId; }
        void topic(AutoTalkTopic topic) {
            if (topic == null) return;
            this.topicId = topic.id();
            this.topicLabel = topic.label();
        }
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
            map.put("topicId", topicId);
            map.put("topicLabel", topicLabel);
            map.put("createdAt", createdAt);
            map.put("textReadyAt", textReadyAt);
            map.put("audioReadyAt", audioReadyAt);
            map.put("audioStatus", audioStatus);
            map.put("readyAt", readyAt);
            map.put("usedAt", usedAt);
            return map;
        }
    }

    private record AutoTalkTopic(String id, String label, boolean enabled, int weight, String prompt) {
        static AutoTalkTopic from(Object value) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, mapValue) -> normalized.put(String.valueOf(key), mapValue));
                return fromMap(normalized);
            }
            return null;
        }

        static AutoTalkTopic fromMap(Map<String, Object> map) {
            if (map == null) return null;
            String id = stringValue(map.get("id"), UUID.randomUUID().toString()).trim();
            String label = stringValue(map.get("label"), id).trim();
            String prompt = stringValue(map.get("prompt"), "").trim();
            if (id.isBlank() || label.isBlank() || prompt.isBlank()) return null;
            boolean enabled = booleanValue(map.get("enabled"), true);
            int weight = Math.max(1, Math.min(5, intValueStatic(map.get("weight"), 1)));
            return new AutoTalkTopic(id, label, enabled, weight, prompt);
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("label", label);
            map.put("enabled", enabled);
            map.put("weight", weight);
            map.put("prompt", prompt);
            return map;
        }

        private static String stringValue(Object value, String fallback) {
            return value == null ? fallback : String.valueOf(value);
        }

        private static boolean booleanValue(Object value, boolean fallback) {
            if (value instanceof Boolean b) return b;
            if (value instanceof String s) return Boolean.parseBoolean(s);
            return fallback;
        }

        private static int intValueStatic(Object value, int fallback) {
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
    }
}
