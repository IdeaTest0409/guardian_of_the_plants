package com.example.guardianplants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.guardianplants.service.LogRetentionService;
import java.time.OffsetDateTime;
import java.util.Locale;
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

    @PostMapping("/logs")
    public ResponseEntity<Map<String, Object>> appLog(@RequestBody AppLogRequest request) throws JsonProcessingException {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }
        String message = valueOrDefault(request.message(), "Client log");
        String category = valueOrDefault(request.category(), "CLIENT");
        String severity = normalizeSeverity(request.severity());
        String detailsJson = objectMapper.writeValueAsString(request.details() == null ? Map.of() : request.details());

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
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
            RETURNING id
            """,
            Long.class,
            valueOrDefault(request.deviceId(), "unknown-client"),
            valueOrDefault(request.appVersion(), "web"),
            severity,
            category,
            message.length() > 2000 ? message.substring(0, 2000) : message,
            detailsJson,
            OffsetDateTime.now()
        );

        return ResponseEntity.ok(Map.of(
            "status", "stored",
            "id", id
        ));
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) return "INFO";
        String normalized = severity.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DEBUG", "INFO", "WARN", "ERROR" -> normalized;
            default -> "INFO";
        };
    }
}
