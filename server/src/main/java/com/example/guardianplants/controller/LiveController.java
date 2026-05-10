package com.example.guardianplants.controller;

import com.example.guardianplants.ApiValidation;
import com.example.guardianplants.dto.ChatRequest;
import com.example.guardianplants.dto.LiveMessageResponse;
import com.example.guardianplants.service.ChatService;
import com.example.guardianplants.service.LiveStateService;
import com.example.guardianplants.service.RequestTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/live")
public class LiveController {
    private static final Logger log = LoggerFactory.getLogger(LiveController.class);

    private final ChatService chatService;
    private final LiveStateService liveStateService;
    private final RequestTraceService traceService;

    public LiveController(ChatService chatService, LiveStateService liveStateService, RequestTraceService traceService) {
        this.chatService = chatService;
        this.liveStateService = liveStateService;
        this.traceService = traceService;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return liveStateService.currentState();
    }

    @PostMapping("/message")
    public LiveMessageResponse message(@RequestBody ChatRequest request) {
        var validationError = ApiValidation.validateChatRequest(request);
        if (validationError.isPresent()) {
            throw new IllegalArgumentException(validationError.get());
        }

        String traceId = traceService.generateTraceId();
        String deviceId = request.deviceId() != null ? request.deviceId() : "unknown";
        traceService.recordReceived(traceId, "live", "device=" + deviceId);
        liveStateService.markThinking(request);

        try {
            String assistantText = chatService.complete(request, traceId);
            Map<String, Object> state = liveStateService.complete(request, assistantText);
            return new LiveMessageResponse(
                UUID.randomUUID().toString(),
                assistantText,
                null,
                null,
                "complete",
                state
            );
        } catch (RuntimeException e) {
            log.error("Live message failed", e);
            traceService.recordError(traceId, "live", "message", e.getMessage());
            Map<String, Object> state = liveStateService.fail(request, e.getMessage());
            return new LiveMessageResponse(
                UUID.randomUUID().toString(),
                "",
                null,
                null,
                "error",
                state
            );
        }
    }
}
