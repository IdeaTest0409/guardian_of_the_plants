package com.example.guardianplants.service;

import com.example.guardianplants.ChatHistoryRepository;
import com.example.guardianplants.dto.ChatRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final WebClient.Builder webClientBuilder;
    private final ProviderResolver providerResolver;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ObjectMapper objectMapper;

    public ChatService(WebClient.Builder webClientBuilder,
                       ProviderResolver providerResolver,
                       ChatHistoryRepository chatHistoryRepository,
                       ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.providerResolver = providerResolver;
        this.chatHistoryRepository = chatHistoryRepository;
        this.objectMapper = objectMapper;
    }

    public SseEmitter stream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        StringBuilder accumulatedContent = new StringBuilder();
        AtomicBoolean completed = new AtomicBoolean(false);

        String deviceId = request.deviceId() != null ? request.deviceId() : "unknown";
        String conversationId = request.conversationId() != null ? request.conversationId() : "default";
        String model = providerResolver.getModel();

        if (request.messages() != null && !request.messages().isEmpty()) {
            var lastUser = request.messages().get(request.messages().size() - 1);
            if ("user".equalsIgnoreCase(lastUser.role())) {
                String metadata = chatHistoryRepository.buildMetadata("server", model, "ok");
                chatHistoryRepository.insert(deviceId, conversationId, "user", lastUser.contentAsString(), metadata);
            }
        }

        Map<String, Object> upstreamBody = buildUpstreamRequest(request, model);

        String apiKey = providerResolver.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("AI API key is not configured");
            try {
                emitter.send(SseEmitter.event().data("{\"error\":\"AI API key is not configured\"}"));
            } catch (IOException e) {
                log.warn("Failed to send error response", e);
            }
            emitter.complete();
            return emitter;
        }

        webClientBuilder.build()
            .post()
            .uri(providerResolver.getBaseUrl() + "/chat/completions")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .bodyValue(upstreamBody)
            .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .onStatus(
                status -> status.isError(),
                response -> response.bodyToMono(String.class)
                    .map(body -> new RuntimeException("Upstream API error: " + response.statusCode() + " - " + body))
            )
            .bodyToFlux(String.class)
            .subscribe(
                chunk -> {
                    if (completed.get()) return;
                    try {
                        if (chunk.startsWith("data: ")) {
                            String data = chunk.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                completed.set(true);
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                String metadata = chatHistoryRepository.buildMetadata("server", model, "completed");
                                chatHistoryRepository.insert(deviceId, conversationId, "assistant", accumulatedContent.toString(), metadata);
                                emitter.complete();
                                return;
                            }
                            try {
                                JsonNode node = objectMapper.readTree(data);
                                JsonNode choices = node.get("choices");
                                if (choices != null && choices.isArray() && choices.size() > 0) {
                                    JsonNode delta = choices.get(0).get("delta");
                                    if (delta != null && delta.has("content")) {
                                        String content = delta.get("content").asText();
                                        if (content != null && !content.isEmpty()) {
                                            accumulatedContent.append(content);
                                        }
                                    }
                                    JsonNode finishReason = choices.get(0).get("finish_reason");
                                    if (finishReason != null && !finishReason.isNull()) {
                                        completed.set(true);
                                        String metadata = chatHistoryRepository.buildMetadata("server", model, "completed");
                                        chatHistoryRepository.insert(deviceId, conversationId, "assistant", accumulatedContent.toString(), metadata);
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("Non-JSON SSE chunk: {}", chunk);
                            }
                            emitter.send(SseEmitter.event().data(chunk));
                        } else {
                            emitter.send(SseEmitter.event().data(chunk));
                        }
                    } catch (IOException e) {
                        log.warn("Failed to send SSE event", e);
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    if (completed.get()) return;
                    log.error("Upstream stream error", error);
                    String metadata = chatHistoryRepository.buildMetadata("server", model, "error: " + error.getMessage());
                    chatHistoryRepository.insert(deviceId, conversationId, "assistant", accumulatedContent.toString(), metadata);
                    emitter.completeWithError(error);
                },
                () -> {
                    if (completed.get()) return;
                    completed.set(true);
                    String metadata = chatHistoryRepository.buildMetadata("server", model, "completed");
                    chatHistoryRepository.insert(deviceId, conversationId, "assistant", accumulatedContent.toString(), metadata);
                    emitter.complete();
                }
            );

        emitter.onTimeout(() -> {
            if (!completed.get()) {
                log.warn("SSE emitter timeout");
                String metadata = chatHistoryRepository.buildMetadata("server", model, "timeout");
                chatHistoryRepository.insert(deviceId, conversationId, "assistant", accumulatedContent.toString(), metadata);
            }
        });

        emitter.onCompletion(() -> completed.set(true));

        return emitter;
    }

    private Map<String, Object> buildUpstreamRequest(ChatRequest request, String model) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", true);

        List<Map<String, Object>> messages = request.messages().stream()
            .map(m -> Map.<String, Object>of("role", m.role(), "content", m.content()))
            .toList();
        body.put("messages", messages);

        Map<String, Object> options = request.options();
        if (options != null) {
            if (options.containsKey("temperature")) {
                body.put("temperature", options.get("temperature"));
            }
            if (options.containsKey("maxTokens")) {
                body.put("max_tokens", options.get("maxTokens"));
            }
            if (options.containsKey("top_p")) {
                body.put("top_p", options.get("top_p"));
            }
            if (options.containsKey("top_k")) {
                body.put("top_k", options.get("top_k"));
            }
        }

        return body;
    }
}
