package com.example.guardianplants;

import com.example.guardianplants.dto.ChatRequest;
import com.example.guardianplants.dto.TtsRequest;
import java.util.Optional;

public final class ApiValidation {
    public static final int MAX_CHAT_MESSAGES = 40;
    public static final int MAX_CHAT_MESSAGE_CHARS = 300_000;
    public static final int MAX_TTS_CHARS = 300;
    public static final int MIN_VOICEVOX_SPEAKER = 0;
    public static final int MAX_VOICEVOX_SPEAKER = 100;

    private ApiValidation() {
    }

    public static Optional<String> validateChatRequest(ChatRequest request) {
        if (request == null) {
            return Optional.of("Request body is required");
        }
        if (request.messages() == null || request.messages().isEmpty()) {
            return Optional.of("messages is required");
        }
        if (request.messages().size() > MAX_CHAT_MESSAGES) {
            return Optional.of("messages must contain at most " + MAX_CHAT_MESSAGES + " items");
        }
        for (var message : request.messages()) {
            if (message == null) {
                return Optional.of("messages must not contain null items");
            }
            if (message.role() == null || message.role().isBlank()) {
                return Optional.of("message.role is required");
            }
            String content = message.contentAsString();
            if (content == null || content.isBlank()) {
                return Optional.of("message.content is required");
            }
            if (content.length() > MAX_CHAT_MESSAGE_CHARS) {
                return Optional.of("message.content must be at most " + MAX_CHAT_MESSAGE_CHARS + " characters");
            }
        }
        return Optional.empty();
    }

    public static Optional<String> validateTtsRequest(TtsRequest request) {
        if (request == null) {
            return Optional.of("Request body is required");
        }
        if (request.text() == null || request.text().isBlank()) {
            return Optional.of("text is required");
        }
        if (request.text().length() > MAX_TTS_CHARS) {
            return Optional.of("text must be at most " + MAX_TTS_CHARS + " characters");
        }
        if (request.speaker() < MIN_VOICEVOX_SPEAKER || request.speaker() > MAX_VOICEVOX_SPEAKER) {
            return Optional.of("speaker is out of range");
        }
        return Optional.empty();
    }
}
