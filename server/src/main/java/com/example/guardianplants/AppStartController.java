package com.example.guardianplants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.guardianplants.service.LogRetentionService;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AppStartController {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LogRetentionService logRetentionService;

    public AppStartController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, LogRetentionService logRetentionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.logRetentionService = logRetentionService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/app-start")
    public ResponseEntity<Map<String, Object>> appStart(@RequestBody AppStartRequest request) throws JsonProcessingException {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }
        String detailsJson = objectMapper.writeValueAsString(
            Map.of(
                "event", "app_start",
                "source", "android",
                "details", request.details() == null ? Map.of() : request.details()
            )
        );

        Long id = jdbcTemplate.queryForObject(
            """
            INSERT INTO app_logs (
                device_id,
                app_version,
                severity,
                category,
                message,
                details,
                occurred_at
            )
            VALUES (?, ?, 'INFO', 'APP_START', 'Android app started', ?::jsonb, ?)
            RETURNING id
            """,
            Long.class,
            request.deviceId(),
            request.appVersion(),
            detailsJson,
            OffsetDateTime.now()
        );
        logRetentionService.pruneAfterAppStartIfNeeded();

        return ResponseEntity.ok(Map.of(
            "status", "stored",
            "id", id
        ));
    }
}
