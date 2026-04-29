package com.example.guardianplants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChatHistoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatHistoryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insert(String deviceId, String conversationId, String role, String content, String metadataJson) {
        if (content == null || content.isBlank()) {
            return;
        }
        jdbcTemplate.update(
            """
            INSERT INTO chat_histories
                (device_id, conversation_id, role, content, metadata, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, NOW())
            """,
            deviceId,
            conversationId,
            role,
            content,
            metadataJson
        );
    }

    public String buildMetadata(String provider, String model, String status) {
        try {
            return objectMapper.writeValueAsString(
                java.util.Map.of(
                    "provider", provider != null ? provider : "server",
                    "model", model != null ? model : "unknown",
                    "status", status != null ? status : "ok"
                )
            );
        } catch (JsonProcessingException e) {
            return "{\"provider\":\"server\",\"status\":\"" + (status != null ? status : "ok") + "\"}";
        }
    }
}
