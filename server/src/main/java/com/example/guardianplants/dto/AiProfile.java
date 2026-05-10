package com.example.guardianplants.dto;

public record AiProfile(
    String id,
    String label,
    String baseUrl,
    String apiKey,
    String model
) {
    public AiProfile withoutSecret() {
        return new AiProfile(id, label, baseUrl, apiKey != null && !apiKey.isBlank() ? "***" : "", model);
    }
}
