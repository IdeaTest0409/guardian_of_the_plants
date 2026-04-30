package com.example.guardianplants.service;

import com.example.guardianplants.config.VoiceVoxConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    private final WebClient webClient;
    private final VoiceVoxConfig config;

    public TtsService(WebClient.Builder webClientBuilder, VoiceVoxConfig config) {
        this.webClient = webClientBuilder.build();
        this.config = config;
    }

    public byte[] synthesize(String text, int speaker) {
        if (!config.isEnabled()) {
            throw new IllegalStateException("TTS is disabled");
        }

        try {
            String audioQuery = webClient.post()
                .uri(config.getBaseUrl() + "/audio_query?text={text}&speaker={speaker}", text, speaker)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return webClient.post()
                .uri(config.getBaseUrl() + "/synthesis?speaker={speaker}", speaker)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(audioQuery)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

        } catch (WebClientResponseException e) {
            log.error("VoiceVOX API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("VoiceVOX synthesis failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("TTS synthesis failed", e);
            throw new RuntimeException("TTS synthesis failed: " + e.getMessage(), e);
        }
    }

    public String getSpeakers() {
        if (!config.isEnabled()) {
            throw new IllegalStateException("TTS is disabled");
        }

        try {
            return webClient.get()
                .uri(config.getBaseUrl() + "/speakers")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        } catch (WebClientResponseException e) {
            log.error("VoiceVOX speakers API error: {}", e.getStatusCode());
            throw new RuntimeException("Failed to fetch speakers: " + e.getMessage(), e);
        }
    }
}
