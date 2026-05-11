package com.example.guardianplants.service;

import com.example.guardianplants.ServerSettingsRepository;
import com.example.guardianplants.dto.AiProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProviderResolver {

    public static final String ACTIVE_AI_PROFILE_KEY = "active_ai_profile_id";

    private final ServerSettingsRepository settingsRepository;
    private final List<AiProfile> profiles;
    private final String defaultProfileId;

    public ProviderResolver(
            @Value("${ai.provider.base-url}") String baseUrl,
            @Value("${ai.provider.api-key}") String apiKey,
            @Value("${ai.provider.model}") String model,
            @Value("${ai.provider.active-profile:default}") String activeProfile,
            @Value("${ai.provider.profiles-json:}") String profilesJson,
            ServerSettingsRepository settingsRepository,
            ObjectMapper objectMapper) {
        this.settingsRepository = settingsRepository;
        this.profiles = loadProfiles(baseUrl, apiKey, model, profilesJson, objectMapper);
        this.defaultProfileId = activeProfile == null || activeProfile.isBlank()
            ? this.profiles.get(0).id()
            : activeProfile;
    }

    public String getBaseUrl() {
        return getActiveProfile().baseUrl();
    }

    public String getApiKey() {
        return getActiveProfile().apiKey();
    }

    public String getModel() {
        return getActiveProfile().model();
    }

    public AiProfile getActiveProfile() {
        String activeId = settingsRepository.get(ACTIVE_AI_PROFILE_KEY).orElse(defaultProfileId);
        return profiles.stream()
            .filter(profile -> profile.id().equals(activeId))
            .findFirst()
            .orElseGet(() -> profiles.get(0));
    }

    public String getActiveProfileId() {
        return getActiveProfile().id();
    }

    public List<AiProfile> getProfiles() {
        return profiles;
    }

    public void setActiveProfile(String profileId) {
        boolean exists = profiles.stream().anyMatch(profile -> profile.id().equals(profileId));
        if (!exists) {
            throw new IllegalArgumentException("Unknown AI profile: " + profileId);
        }
        settingsRepository.set(ACTIVE_AI_PROFILE_KEY, profileId);
    }

    private List<AiProfile> loadProfiles(
        String baseUrl,
        String apiKey,
        String model,
        String profilesJson,
        ObjectMapper objectMapper
    ) {
        if (profilesJson != null && !profilesJson.isBlank()) {
            try {
                List<AiProfile> configured = objectMapper.readValue(profilesJson, new TypeReference<List<AiProfile>>() {});
                if (!configured.isEmpty()) {
                    return withBuiltInCloudProfiles(configured, baseUrl, apiKey);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("AI_PROFILES_JSON is invalid", e);
            }
        }
        return withBuiltInCloudProfiles(List.of(new AiProfile(
            "default",
            "Default AI",
            baseUrl,
            apiKey,
            model
        )), baseUrl, apiKey);
    }

    private List<AiProfile> withBuiltInCloudProfiles(List<AiProfile> configured, String baseUrl, String apiKey) {
        List<AiProfile> result = new ArrayList<>(configured);
        String ollamaBaseUrl = configured.stream()
            .filter(profile -> profile.baseUrl() != null && profile.baseUrl().contains("ollama.com"))
            .map(AiProfile::baseUrl)
            .findFirst()
            .orElse(baseUrl);
        String ollamaApiKey = configured.stream()
            .filter(profile -> profile.baseUrl() != null && profile.baseUrl().contains("ollama.com"))
            .map(AiProfile::apiKey)
            .filter(key -> key != null && !key.isBlank())
            .findFirst()
            .orElse(apiKey);
        addProfileIfMissing(result, new AiProfile(
            "ollama-cloud-gemma4-31b",
            "Ollama Cloud gemma4:31b",
            ollamaBaseUrl,
            ollamaApiKey,
            "gemma4:31b-cloud"
        ));
        addProfileIfMissing(result, new AiProfile(
            "ollama-cloud-deepseek-v4-flash",
            "Ollama Cloud deepseek-v4-flash",
            ollamaBaseUrl,
            ollamaApiKey,
            "deepseek-v4-flash"
        ));
        return List.copyOf(result);
    }

    private void addProfileIfMissing(List<AiProfile> profiles, AiProfile profile) {
        boolean exists = profiles.stream().anyMatch(candidate ->
            candidate.id().equals(profile.id()) || candidate.model().equals(profile.model()));
        if (!exists) {
            profiles.add(profile);
        }
    }
}
