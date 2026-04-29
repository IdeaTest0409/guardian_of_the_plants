package com.example.guardianplants.dto;

import java.util.List;
import java.util.Map;

public record ChatRequest(
    String deviceId,
    String conversationId,
    List<ServerMessage> messages,
    Map<String, Object> options
) {
}
