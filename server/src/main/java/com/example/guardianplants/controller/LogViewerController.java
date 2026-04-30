package com.example.guardianplants.controller;

import com.example.guardianplants.LogViewerRepository;
import com.example.guardianplants.service.TtsHealthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogViewerController {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogViewerRepository logViewerRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TtsHealthService ttsHealthService;

    public LogViewerController(LogViewerRepository logViewerRepository, JdbcTemplate jdbcTemplate, TtsHealthService ttsHealthService) {
        this.logViewerRepository = logViewerRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.ttsHealthService = ttsHealthService;
    }

    @GetMapping("/chat")
    public List<Map<String, Object>> chatLogs(@RequestParam(defaultValue = "100") int limit) {
        return logViewerRepository.getChatLogs(Math.min(limit, 500));
    }

    @GetMapping("/app")
    public List<Map<String, Object>> appLogs(@RequestParam(defaultValue = "100") int limit) {
        return logViewerRepository.getAppLogs(Math.min(limit, 500));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("server", "ok");
        result.put("timestamp", System.currentTimeMillis());

        try {
            Integer dbCount = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            result.put("database", dbCount != null ? "ok" : "error");
        } catch (Exception e) {
            result.put("database", "error: " + e.getMessage());
        }

        try {
            Long chatCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_histories", Long.class);
            result.put("chatHistoryCount", chatCount);
        } catch (Exception e) {
            result.put("chatHistoryCount", "error");
        }

        try {
            Long appLogCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_logs", Long.class);
            result.put("appLogCount", appLogCount);
        } catch (Exception e) {
            result.put("appLogCount", "error");
        }

        try {
            Long errorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_histories WHERE metadata->>'status' LIKE 'error%'", Long.class);
            result.put("chatErrorCount", errorCount);
        } catch (Exception e) {
            result.put("chatErrorCount", "error");
        }

        boolean voiceVoxHealthy = ttsHealthService.checkVoiceVoxHealth();
        result.put("voicevox", voiceVoxHealthy ? "ok" : "unreachable");

        return result;
    }

    @GetMapping("/download")
    public ResponseEntity<String> downloadLogs(
            @RequestParam(defaultValue = "1") int hours,
            @RequestParam(defaultValue = "all") String type) {
        int cappedHours = Math.min(Math.max(hours, 1), 24);
        String sinceTimestamp = Instant.now().minusSeconds(cappedHours * 3600L)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        StringBuilder sb = new StringBuilder();
        String nowStr = Instant.now().atZone(ZoneOffset.UTC).format(TS_FMT);
        sb.append("=== Guardian Plants Log Export ===\n");
        sb.append("Generated: ").append(nowStr).append(" UTC\n");
        sb.append("Period: Last ").append(cappedHours).append(" hour(s) (since ").append(sinceTimestamp).append(")\n");
        sb.append("=====================================\n\n");

        if ("all".equals(type) || "chat".equals(type)) {
            sb.append("=== CHAT HISTORY ===\n\n");
            List<Map<String, Object>> chatLogs = logViewerRepository.getChatLogsSince(sinceTimestamp);
            sb.append("Count: ").append(chatLogs.size()).append("\n\n");
            for (Map<String, Object> row : chatLogs) {
                String ts = formatTs(row.get("created_at"));
                String deviceId = safeStr(row.get("device_id"));
                String role = safeStr(row.get("role"));
                String content = safeStr(row.get("content_preview"));
                String status = safeStr(row.get("status"));
                String convId = safeStr(row.get("conversation_id"));
                String model = safeStr(row.get("model"));
                sb.append("[").append(ts).append("] ");
                sb.append("device=").append(deviceId);
                if (!convId.isEmpty()) sb.append(" conv=").append(convId);
                sb.append(" role=").append(role);
                if (!status.isEmpty()) sb.append(" status=").append(status);
                if (!model.isEmpty()) sb.append(" model=").append(model);
                sb.append("\n  ").append(content).append("\n\n");
            }
        }

        if ("all".equals(type) || "app".equals(type)) {
            sb.append("=== APP LOGS ===\n\n");
            List<Map<String, Object>> appLogs = logViewerRepository.getAppLogsSince(sinceTimestamp);
            sb.append("Count: ").append(appLogs.size()).append("\n\n");
            for (Map<String, Object> row : appLogs) {
                String ts = formatTs(row.get("received_at"));
                String deviceId = safeStr(row.get("device_id"));
                String severity = safeStr(row.get("severity"));
                String category = safeStr(row.get("category"));
                String message = safeStr(row.get("message_preview"));
                String appVersion = safeStr(row.get("app_version"));
                Object details = row.get("details");
                sb.append("[").append(ts).append("] ");
                sb.append(severity).append(" / ").append(category);
                sb.append(" device=").append(deviceId);
                if (!appVersion.isEmpty()) sb.append(" v=").append(appVersion);
                sb.append("\n  ").append(message).append("\n");
                if (details != null) {
                    sb.append("  ").append(details).append("\n");
                }
                sb.append("\n");
            }
        }

        String filename = "guardian-logs-" + Instant.now().atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.TEXT_PLAIN)
            .body(sb.toString());
    }

    private String formatTs(Object ts) {
        if (ts == null) return "unknown";
        try {
            Instant instant;
            if (ts instanceof java.sql.Timestamp) {
                instant = ((java.sql.Timestamp) ts).toInstant();
            } else {
                instant = Instant.parse(ts.toString());
            }
            return instant.atZone(ZoneOffset.UTC).format(TS_FMT);
        } catch (Exception e) {
            return ts.toString();
        }
    }

    private String safeStr(Object val) {
        return val != null ? val.toString() : "";
    }
}
