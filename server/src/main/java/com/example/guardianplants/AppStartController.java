package com.example.guardianplants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
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

    public AppStartController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/app-start")
    public Map<String, Object> appStart(@RequestBody AppStartRequest request) throws JsonProcessingException {
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

        return Map.of(
            "status", "stored",
            "id", id
        );
    }
}
