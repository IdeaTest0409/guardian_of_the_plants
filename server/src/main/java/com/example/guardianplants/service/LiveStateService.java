package com.example.guardianplants.service;

import com.example.guardianplants.dto.ChatRequest;
import com.example.guardianplants.dto.ServerMessage;
import com.fasterxml.jackson.databind.JsonNode;
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

    private static final int MAX_USER_DISPLAY_CHARS = 120;
    private static final int MAX_ASSISTANT_DISPLAY_CHARS = 280;
    private static final String DEFAULT_POSE_PRESET = "auto";
    private static final String DEFAULT_PLANT_IMAGE_SOURCE = "smartphone";
    private static final double DEFAULT_AUDIO_PLAYBACK_RATE = 1.0;

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
        new LiveSettings(DEFAULT_POSE_PRESET, DEFAULT_PLANT_IMAGE_SOURCE, DEFAULT_AUDIO_PLAYBACK_RATE)
    );

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
        LiveSettings next = new LiveSettings(
            normalizePosePreset(posePreset),
            normalizePlantImageSource(plantImageSource),
            normalizeAudioPlaybackRate(audioPlaybackRate)
        );
        settings.set(next);
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

    private record LiveSettings(String posePreset, String plantImageSource, double audioPlaybackRate) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("posePreset", posePreset);
            map.put("plantImageSource", plantImageSource);
            map.put("audioPlaybackRate", audioPlaybackRate);
            return map;
        }
    }
}
