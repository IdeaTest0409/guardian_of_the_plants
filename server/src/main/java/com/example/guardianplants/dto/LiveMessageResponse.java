package com.example.guardianplants.dto;

import java.util.Map;

public record LiveMessageResponse(
    String messageId,
    String assistantText,
    String audioUrl,
    String audioFormat,
    String status,
    Map<String, Object> state
) {
}
