package com.example.guardianplants.controller;

import com.example.guardianplants.dto.ChatRequest;
import com.example.guardianplants.service.ChatService;
import com.example.guardianplants.service.RequestTraceService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final RequestTraceService traceService;

    public ChatController(ChatService chatService, RequestTraceService traceService) {
        this.chatService = chatService;
        this.traceService = traceService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
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
}
