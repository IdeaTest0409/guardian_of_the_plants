package com.example.guardianplants.service;

import com.example.guardianplants.ServerSettingsRepository;
import com.example.guardianplants.dto.ChatRequest;
import com.example.guardianplants.dto.ServerMessage;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LiveStateService {
    private static final Logger log = LoggerFactory.getLogger(LiveStateService.class);

    private static final int MAX_USER_DISPLAY_CHARS = 120;
    private static final int MAX_ASSISTANT_DISPLAY_CHARS = 280;
    private static final String DEFAULT_POSE_PRESET = "auto";
    private static final String DEFAULT_PLANT_IMAGE_SOURCE = "smartphone";
    private static final double DEFAULT_AUDIO_PLAYBACK_RATE = 1.0;
    private static final boolean DEFAULT_BGM_ENABLED = true;
    private static final double DEFAULT_BGM_VOLUME = 0.10;
    private static final String DEFAULT_BGM_TRACK = "peaceful_ambient";
    private static final String SETTING_POSE_PRESET = "live.posePreset";
    private static final String SETTING_PLANT_IMAGE_SOURCE = "live.plantImageSource";
    private static final String SETTING_AUDIO_PLAYBACK_RATE = "live.audioPlaybackRate";
    private static final String SETTING_BGM_ENABLED = "live.bgm.enabled";
    private static final String SETTING_BGM_VOLUME = "live.bgm.volume";
    private static final String SETTING_BGM_TRACK = "live.bgm.track";

    private final ServerSettingsRepository settingsRepository;

    private final AtomicReference<LiveState> state = new AtomicReference<>(
        new LiveState(
            UUID.randomUUID().toString(),
            "idle",
            null,
            null,
            null,
            null,
            null,
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        )
    );
    private final AtomicReference<LiveSettings> settings = new AtomicReference<>(
        new LiveSettings(DEFAULT_POSE_PRESET, DEFAULT_PLANT_IMAGE_SOURCE, DEFAULT_AUDIO_PLAYBACK_RATE,
            DEFAULT_BGM_ENABLED, DEFAULT_BGM_VOLUME, DEFAULT_BGM_TRACK)
    );

    public LiveStateService(ServerSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @PostConstruct
    void loadPersistedSettings() {
        settings.set(loadSettings());
    }

    public Map<String, Object> currentState() {
        return state.get().toMap();
    }

    public Map<String, Object> currentSettings() {
        return settings.get().toMap();
    }

    public Map<String, Object> updateSettings(Map<String, Object> request) {
        LiveSettings previous = settings.get();
        String posePreset = request == null
            ? previous.posePreset()
            : String.valueOf(request.getOrDefault("posePreset", previous.posePreset()));
        String plantImageSource = request == null
            ? previous.plantImageSource()
            : String.valueOf(request.getOrDefault("plantImageSource", previous.plantImageSource()));
        double audioPlaybackRate = request == null
            ? previous.audioPlaybackRate()
            : doubleValue(request.get("audioPlaybackRate"), previous.audioPlaybackRate());
        boolean bgmEnabled = request == null
            ? previous.bgmEnabled()
            : booleanValue(request.get("bgmEnabled"), previous.bgmEnabled());
        double bgmVolume = request == null
            ? previous.bgmVolume()
            : doubleValue(request.get("bgmVolume"), previous.bgmVolume());
        String bgmTrack = request == null
            ? previous.bgmTrack()
            : String.valueOf(request.getOrDefault("bgmTrack", previous.bgmTrack()));
        LiveSettings next = new LiveSettings(
            normalizePosePreset(posePreset),
            normalizePlantImageSource(plantImageSource),
            normalizeAudioPlaybackRate(audioPlaybackRate),
            bgmEnabled,
            normalizeBgmVolume(bgmVolume),
            normalizeBgmTrack(bgmTrack)
        );
        settings.set(next);
        persistSettings(next);
        return next.toMap();
    }

    public Map<String, Object> updatePlantImage(String plantImageDataUrl) {
        LiveState previous = state.get();
        String nextImage = sanitizeImageDataUrl(plantImageDataUrl);
        LiveState next = new LiveState(
            previous.sessionId(),
            previous.status(),
            previous.userText(),
            previous.assistantText(),
            nextImage,
            previous.audioUrl(),
            null,
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
        state.set(next);
        return next.toMap();
    }

    public void markThinking(ChatRequest request) {
        LiveState previous = state.get();
        state.set(new LiveState(
            previous.sessionId(),
            "thinking",
            latestDisplayUserText(request),
            previous.assistantText(),
            latestImageDataUrl(request, previous.plantImageDataUrl()),
            previous.audioUrl(),
            null,
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
    }

    public Map<String, Object> complete(ChatRequest request, String assistantText) {
        return complete(request, assistantText, null);
    }

    public Map<String, Object> complete(ChatRequest request, String assistantText, String audioUrl) {
        LiveState previous = state.get();
        LiveState next = new LiveState(
            previous.sessionId(),
            "complete",
            latestDisplayUserText(request),
            sanitizeDisplayText(assistantText, MAX_ASSISTANT_DISPLAY_CHARS),
            latestImageDataUrl(request, previous.plantImageDataUrl()),
            audioUrl,
            null,
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
        state.set(next);
        return next.toMap();
    }

    public Map<String, Object> fail(ChatRequest request, String errorMessage) {
        LiveState previous = state.get();
        LiveState next = new LiveState(
            previous.sessionId(),
            "error",
            latestDisplayUserText(request),
            previous.assistantText(),
            latestImageDataUrl(request, previous.plantImageDataUrl()),
            previous.audioUrl(),
            sanitizeDisplayText(errorMessage, 180),
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
        state.set(next);
        return next.toMap();
    }

    private String latestDisplayUserText(ChatRequest request) {
        if (request == null || request.messages() == null) return null;
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            ServerMessage message = request.messages().get(i);
            if ("user".equalsIgnoreCase(message.role())) {
                String plainText = firstTextPart(message.content());
                if (isInternalInstruction(plainText)) {
                    return "自動トーク";
                }
                return sanitizeDisplayText(plainText, MAX_USER_DISPLAY_CHARS);
            }
        }
        return null;
    }

    private String latestImageDataUrl(ChatRequest request, String fallback) {
        if (!"smartphone".equals(settings.get().plantImageSource())) {
            return fallback;
        }
        if (request == null || request.messages() == null) return fallback;
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            String found = findImageDataUrl(request.messages().get(i).content());
            if (found != null) return found;
        }
        return fallback;
    }

    private String sanitizeImageDataUrl(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isBlank()) return null;
        if (!trimmed.startsWith("data:image/")) {
            throw new IllegalArgumentException("plantImageDataUrl must be a data:image URL");
        }
        return trimmed;
    }

    @SuppressWarnings("unchecked")
    private String firstTextPart(Object value) {
        if (value == null) return null;
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof JsonNode node) {
            if (node.isTextual()) {
                return node.asText();
            }
            if (node.isArray()) {
                for (JsonNode child : node) {
                    String found = firstTextPart(child);
                    if (found != null) return found;
                }
            }
            if (node.isObject()) {
                if (node.has("type") && "text".equals(node.get("type").asText("")) && node.has("text")) {
                    return node.get("text").asText();
                }
                var fields = node.fields();
                while (fields.hasNext()) {
                    String found = firstTextPart(fields.next().getValue());
                    if (found != null) return found;
                }
            }
            return null;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String found = firstTextPart(item);
                if (found != null) return found;
            }
        }
        if (value instanceof Map<?, ?> map) {
            Object type = map.get("type");
            Object text = map.get("text");
            if ("text".equals(type) && text instanceof String s) {
                return s;
            }
            for (Object item : map.values()) {
                String found = firstTextPart(item);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String findImageDataUrl(Object value) {
        if (value == null) return null;
        if (value instanceof String s) {
            return s.startsWith("data:image/") ? s : null;
        }
        if (value instanceof JsonNode node) {
            if (node.isTextual()) {
                String text = node.asText();
                return text.startsWith("data:image/") ? text : null;
            }
            if (node.isArray()) {
                for (JsonNode child : node) {
                    String found = findImageDataUrl(child);
                    if (found != null) return found;
                }
            }
            if (node.isObject()) {
                if (node.has("url")) {
                    String url = node.get("url").asText("");
                    if (url.startsWith("data:image/")) return url;
                }
                var fields = node.fields();
                while (fields.hasNext()) {
                    String found = findImageDataUrl(fields.next().getValue());
                    if (found != null) return found;
                }
            }
            return null;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String found = findImageDataUrl(item);
                if (found != null) return found;
            }
        }
        if (value instanceof Map<?, ?> map) {
            Object url = map.get("url");
            if (url instanceof String s && s.startsWith("data:image/")) return s;
            for (Object item : map.values()) {
                String found = findImageDataUrl(item);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean isInternalInstruction(String text) {
        if (text == null) return false;
        return text.contains("ユーザーにはこの指示文を見せず")
            || text.contains("Server strict response rules")
            || text.contains("strict response rules")
            || text.contains("自動雑談です。");
    }

    private String sanitizeDisplayText(String text, int maxChars) {
        if (text == null) return null;
        String sanitized = text
            .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
            .replaceAll("\\s+", " ")
            .trim();
        if (sanitized.isBlank()) return null;
        if (sanitized.length() <= maxChars) return sanitized;
        return sanitized.substring(0, maxChars) + "...";
    }

    private String normalizePosePreset(String posePreset) {
        if (posePreset == null || posePreset.isBlank()) return DEFAULT_POSE_PRESET;
        return switch (posePreset.trim().toLowerCase()) {
            case "auto", "relaxed", "arms-down", "raw", "small", "large",
                "wave", "happy-bounce", "dance-soft", "walk-random", "auto-random" -> posePreset.trim().toLowerCase();
            default -> DEFAULT_POSE_PRESET;
        };
    }

    private String normalizePlantImageSource(String plantImageSource) {
        if (plantImageSource == null || plantImageSource.isBlank()) return DEFAULT_PLANT_IMAGE_SOURCE;
        return switch (plantImageSource.trim().toLowerCase()) {
            case "smartphone", "pc" -> plantImageSource.trim().toLowerCase();
            default -> DEFAULT_PLANT_IMAGE_SOURCE;
        };
    }

    private double doubleValue(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double normalizeAudioPlaybackRate(double value) {
        double clamped = Math.max(0.5, Math.min(4.0, value));
        return Math.round(clamped * 10.0) / 10.0;
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s && !s.isBlank()) return Boolean.parseBoolean(s.trim());
        return fallback;
    }

    private double normalizeBgmVolume(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return Math.round(clamped * 100.0) / 100.0;
    }

    private String normalizeBgmTrack(String value) {
        if (value == null || value.isBlank()) return DEFAULT_BGM_TRACK;
        return switch (value.trim()) {
            case "peaceful_ambient", "jazz_1", "jazz_2", "jazz_3", "none" -> value.trim();
            default -> DEFAULT_BGM_TRACK;
        };
    }

    private LiveSettings loadSettings() {
        try {
            String posePreset = settingsRepository.get(SETTING_POSE_PRESET).orElse(DEFAULT_POSE_PRESET);
            String plantImageSource = settingsRepository.get(SETTING_PLANT_IMAGE_SOURCE).orElse(DEFAULT_PLANT_IMAGE_SOURCE);
            double audioPlaybackRate = settingsRepository.get(SETTING_AUDIO_PLAYBACK_RATE)
                .map(value -> doubleValue(value, DEFAULT_AUDIO_PLAYBACK_RATE))
                .orElse(DEFAULT_AUDIO_PLAYBACK_RATE);
            boolean bgmEnabled = settingsRepository.get(SETTING_BGM_ENABLED)
                .map(value -> booleanValue(value, DEFAULT_BGM_ENABLED))
                .orElse(DEFAULT_BGM_ENABLED);
            double bgmVolume = settingsRepository.get(SETTING_BGM_VOLUME)
                .map(value -> doubleValue(value, DEFAULT_BGM_VOLUME))
                .orElse(DEFAULT_BGM_VOLUME);
            String bgmTrack = settingsRepository.get(SETTING_BGM_TRACK).orElse(DEFAULT_BGM_TRACK);
            return new LiveSettings(
                normalizePosePreset(posePreset),
                normalizePlantImageSource(plantImageSource),
                normalizeAudioPlaybackRate(audioPlaybackRate),
                bgmEnabled,
                normalizeBgmVolume(bgmVolume),
                normalizeBgmTrack(bgmTrack)
            );
        } catch (RuntimeException e) {
            log.warn("Failed to load live settings; using defaults", e);
            return new LiveSettings(DEFAULT_POSE_PRESET, DEFAULT_PLANT_IMAGE_SOURCE, DEFAULT_AUDIO_PLAYBACK_RATE,
                DEFAULT_BGM_ENABLED, DEFAULT_BGM_VOLUME, DEFAULT_BGM_TRACK);
        }
    }

    private void persistSettings(LiveSettings next) {
        try {
            settingsRepository.set(SETTING_POSE_PRESET, next.posePreset());
            settingsRepository.set(SETTING_PLANT_IMAGE_SOURCE, next.plantImageSource());
            settingsRepository.set(SETTING_AUDIO_PLAYBACK_RATE, String.valueOf(next.audioPlaybackRate()));
            settingsRepository.set(SETTING_BGM_ENABLED, String.valueOf(next.bgmEnabled()));
            settingsRepository.set(SETTING_BGM_VOLUME, String.valueOf(next.bgmVolume()));
            settingsRepository.set(SETTING_BGM_TRACK, next.bgmTrack());
        } catch (RuntimeException e) {
            log.warn("Failed to persist live settings", e);
        }
    }

    private record LiveState(
        String sessionId,
        String status,
        String userText,
        String assistantText,
        String plantImageDataUrl,
        String audioUrl,
        String error,
        String updatedAt
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sessionId", sessionId);
            map.put("status", status);
            map.put("userText", userText);
            map.put("assistantText", assistantText);
            map.put("plantImageDataUrl", plantImageDataUrl);
            map.put("audioUrl", audioUrl);
            map.put("error", error);
            map.put("updatedAt", updatedAt);
            return map;
        }
    }

    private record LiveSettings(String posePreset, String plantImageSource, double audioPlaybackRate,
                                boolean bgmEnabled, double bgmVolume, String bgmTrack) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("posePreset", posePreset);
            map.put("plantImageSource", plantImageSource);
            map.put("audioPlaybackRate", audioPlaybackRate);
            map.put("bgmEnabled", bgmEnabled);
            map.put("bgmVolume", bgmVolume);
            map.put("bgmTrack", bgmTrack);
            map.put("bgmTracks", List.of(
                Map.of(
                    "id", "peaceful_ambient",
                    "label", "Peaceful Ambient Music",
                    "url", "/live/assets/audio/peaceful-ambient-music.mp3",
                    "license", "CC BY 4.0",
                    "source", "Orange Free Sounds / Alexander Blu"
                ),
                Map.of(
                    "id", "jazz_1",
                    "label", "Jazz Guitar 1",
                    "url", "/live/assets/audio/1-jazz.mp3",
                    "license", "CC0 1.0",
                    "source", "HoliznaCC0 / Free Music Archive"
                ),
                Map.of(
                    "id", "jazz_2",
                    "label", "Jazz Guitar 2",
                    "url", "/live/assets/audio/2-jazz.mp3",
                    "license", "CC0 1.0",
                    "source", "HoliznaCC0 / Free Music Archive"
                ),
                Map.of(
                    "id", "jazz_3",
                    "label", "Jazz Guitar 3",
                    "url", "/live/assets/audio/3-jazz.mp3",
                    "license", "CC0 1.0",
                    "source", "HoliznaCC0 / Free Music Archive"
                )
            ));
            return map;
        }
    }
}
