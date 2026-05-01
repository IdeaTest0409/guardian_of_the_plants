package com.example.guardianplants.controller;

import com.example.guardianplants.ApiValidation;
import com.example.guardianplants.dto.ChatRequest;
import com.example.guardianplants.service.ChatService;
import com.example.guardianplants.service.RequestTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final RequestTraceService traceService;

    public ChatController(ChatService chatService, RequestTraceService traceService) {
        this.chatService = chatService;
        this.traceService = traceService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        var validationError = ApiValidation.validateChatRequest(request);
        if (validationError.isPresent()) {
            String traceId = traceService.generateTraceId();
            String deviceId = request != null && request.deviceId() != null ? request.deviceId() : "unknown";
            String error = validationError.get();
            log.warn("Chat request validation failed: deviceId={} error={}", deviceId, error);
            traceService.recordReceived(traceId, "chat", "device=" + deviceId);
            traceService.recordError(traceId, "chat", "validation", error);
            SseEmitter emitter = new SseEmitter(5_000L);
            try {
                emitter.send(SseEmitter.event().data("{\"error\":\"" + escapeJson(error) + "\"}"));
            } catch (IOException ignored) {
                // Client disconnected before validation error could be sent.
            }
            emitter.complete();
            return emitter;
        }

        String traceId = traceService.generateTraceId();
        String deviceId = request.deviceId() != null ? request.deviceId() : "unknown";
        String modelInfo = "device=" + deviceId;
        if (request.messages() != null && !request.messages().isEmpty()) {
            var lastUser = request.messages().get(request.messages().size() - 1);
            if ("user".equalsIgnoreCase(lastUser.role())) {
                modelInfo += " msg=" + lastUser.contentAsString().substring(0, Math.min(100, lastUser.contentAsString().length()));
            }
        }
        traceService.recordReceived(traceId, "chat", modelInfo);
        return chatService.stream(request, traceId);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
