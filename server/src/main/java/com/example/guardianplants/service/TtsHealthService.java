package com.example.guardianplants.service;

import com.example.guardianplants.config.VoiceVoxConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class TtsHealthService {

    private static final Logger log = LoggerFactory.getLogger(TtsHealthService.class);

    private final WebClient webClient;
    private final VoiceVoxConfig config;

    public TtsHealthService(WebClient.Builder webClientBuilder, VoiceVoxConfig config) {
        this.webClient = webClientBuilder.build();
        this.config = config;
    }

    public boolean checkVoiceVoxHealth() {
        if (!config.isEnabled()) {
            log.debug("VoiceVOX is disabled");
            return false;
        }
        try {
            String result = webClient.get()
                .uri(config.getBaseUrl() + "/version")
                .retrieve()
                .bodyToMono(String.class)
                .block();
            boolean healthy = result != null && !result.isBlank();
            log.info("VoiceVOX health check: {} (version={})", healthy ? "ok" : "failed", result);
            return healthy;
        } catch (Exception e) {
            log.warn("VoiceVOX health check failed: {}", e.getMessage());
            return false;
        }
    }
}
