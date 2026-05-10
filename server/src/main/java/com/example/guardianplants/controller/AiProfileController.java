package com.example.guardianplants.controller;

import com.example.guardianplants.dto.AiProfile;
import com.example.guardianplants.service.ProviderResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiProfileController {

    private final ProviderResolver providerResolver;
    private final WebClient.Builder webClientBuilder;

    public AiProfileController(ProviderResolver providerResolver, WebClient.Builder webClientBuilder) {
        this.providerResolver = providerResolver;
        this.webClientBuilder = webClientBuilder;
    }

    @GetMapping("/profiles")
    public Map<String, Object> profiles() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeProfileId", providerResolver.getActiveProfileId());
        result.put("profiles", providerResolver.getProfiles().stream()
            .map(AiProfile::withoutSecret)
            .toList());
        return result;
    }

    @PostMapping("/active")
    public Map<String, Object> setActive(@RequestBody Map<String, String> body) {
        String profileId = body.get("profileId");
        providerResolver.setActiveProfile(profileId);
        return profiles();
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(@RequestBody Map<String, String> body) {
        String profileId = body.get("profileId");
        AiProfile profile = providerResolver.getProfiles().stream()
            .filter(candidate -> candidate.id().equals(profileId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown AI profile: " + profileId));

        String modelsUrl = profile.baseUrl().trim().replaceAll("/+$", "") + "/models";
        long started = System.currentTimeMillis();
        try {
            String response = webClientBuilder.build()
                .get()
                .uri(modelsUrl)
                .header("Authorization", "Bearer " + nullToEmpty(profile.apiKey()))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))
                .block();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("profileId", profile.id());
            result.put("durationMs", System.currentTimeMillis() - started);
            result.put("responsePreview", response != null ? response.substring(0, Math.min(500, response.length())) : "");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("profileId", profile.id());
            result.put("durationMs", System.currentTimeMillis() - started);
            result.put("error", e.getMessage());
            return ResponseEntity.status(502).body(result);
        }
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
