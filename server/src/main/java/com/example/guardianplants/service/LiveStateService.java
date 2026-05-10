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

    public Map<String, Object> currentState() {
        return state.get().toMap();
    }

    public void markThinking(ChatRequest request) {
        LiveState previous = state.get();
        state.set(new LiveState(
            previous.sessionId(),
            "thinking",
            latestUserText(request),
            previous.assistantText(),
            latestImageDataUrl(request, previous.plantImageDataUrl()),
            previous.audioUrl(),
            null,
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
    }

    public Map<String, Object> complete(ChatRequest request, String assistantText) {
        LiveState previous = state.get();
        LiveState next = new LiveState(
            previous.sessionId(),
            "complete",
            latestUserText(request),
            assistantText,
            latestImageDataUrl(request, previous.plantImageDataUrl()),
            null,
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
            latestUserText(request),
            previous.assistantText(),
            latestImageDataUrl(request, previous.plantImageDataUrl()),
            previous.audioUrl(),
            errorMessage,
            OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
        state.set(next);
        return next.toMap();
    }

    private String latestUserText(ChatRequest request) {
        if (request == null || request.messages() == null) return null;
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            ServerMessage message = request.messages().get(i);
            if ("user".equalsIgnoreCase(message.role())) {
                return message.contentAsString();
            }
        }
        return null;
    }

    private String latestImageDataUrl(ChatRequest request, String fallback) {
        if (request == null || request.messages() == null) return fallback;
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            String found = findImageDataUrl(request.messages().get(i).content());
            if (found != null) return found;
        }
        return fallback;
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
}
