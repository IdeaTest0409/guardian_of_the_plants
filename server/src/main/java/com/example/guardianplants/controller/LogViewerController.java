package com.example.guardianplants.controller;

import com.example.guardianplants.LogViewerRepository;
import com.example.guardianplants.RequestTraceRepository;
import com.example.guardianplants.ServerVersionInfo;
import com.example.guardianplants.service.TtsHealthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
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
    private final RequestTraceRepository requestTraceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TtsHealthService ttsHealthService;
    private final ServerVersionInfo serverVersionInfo;

    public LogViewerController(LogViewerRepository logViewerRepository, RequestTraceRepository requestTraceRepository, JdbcTemplate jdbcTemplate, TtsHealthService ttsHealthService, ServerVersionInfo serverVersionInfo) {
        this.logViewerRepository = logViewerRepository;
        this.requestTraceRepository = requestTraceRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.ttsHealthService = ttsHealthService;
        this.serverVersionInfo = serverVersionInfo;
    }

    @GetMapping("/chat")
    public List<Map<String, Object>> chatLogs(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Integer hours) {
        int cappedLimit = Math.min(Math.max(limit, 1), 500);
        if (hours == null) {
            return logViewerRepository.getChatLogs(cappedLimit);
        }
        return logViewerRepository.getChatLogsSince(since(hours), cappedLimit);
    }

    @GetMapping("/app")
    public List<Map<String, Object>> appLogs(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Integer hours) {
        int cappedLimit = Math.min(Math.max(limit, 1), 500);
        if (hours == null) {
            return logViewerRepository.getAppLogs(cappedLimit);
        }
        return logViewerRepository.getAppLogsSince(since(hours), cappedLimit);
    }

    @GetMapping("/flow")
    public List<Map<String, Object>> requestFlows(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "6") int hours) {
        return requestTraceRepository.getRecentTraces(cappedHours(hours)).stream()
            .limit(Math.min(Math.max(limit, 1), 200))
            .toList();
    }

    @GetMapping("/flow/{traceId}")
    public List<Map<String, Object>> traceDetail(@PathVariable String traceId) {
        return requestTraceRepository.getTraceSteps(traceId);
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

        try {
            Long traceCount = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT trace_id) FROM request_traces", Long.class);
            result.put("traceCount", traceCount);
            Long traceErrorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT trace_id) FROM request_traces WHERE status = 'error'", Long.class);
            result.put("traceErrorCount", traceErrorCount);
        } catch (Exception e) {
            result.put("traceCount", 0);
            result.put("traceErrorCount", 0);
        }

        boolean voiceVoxHealthy = ttsHealthService.checkVoiceVoxHealth();
        result.put("voicevox", voiceVoxHealthy ? "ok" : "unreachable");

        return result;
    }

    @GetMapping("/version")
    public Map<String, Object> version() {
        return serverVersionInfo.asMap();
    }

    @GetMapping("/download")
    public ResponseEntity<String> downloadLogs(
            @RequestParam(defaultValue = "1") int hours,
            @RequestParam(defaultValue = "all") String type) {
        int cappedHours = Math.min(Math.max(hours, 1), 24);
        OffsetDateTime since = since(cappedHours);

        StringBuilder sb = new StringBuilder();
        String nowStr = Instant.now().atZone(ZoneOffset.UTC).format(TS_FMT);
        sb.append("=== Guardian Plants Log Export ===\n");
        sb.append("Generated: ").append(nowStr).append(" UTC\n");
        sb.append("Period: Last ").append(cappedHours).append(" hour(s) (since ").append(since).append(")\n");
        sb.append("=====================================\n\n");

        if ("all".equals(type) || "chat".equals(type)) {
            sb.append("=== CHAT HISTORY ===\n\n");
            List<Map<String, Object>> chatLogs = logViewerRepository.getChatLogsSince(since);
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
            List<Map<String, Object>> appLogs = logViewerRepository.getAppLogsSince(since);
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

        if ("all".equals(type) || "flow".equals(type)) {
            sb.append("=== REQUEST FLOWS ===\n\n");
            List<Map<String, Object>> traces = requestTraceRepository.getRecentTraces(cappedHours);
            sb.append("Count: ").append(traces.size()).append("\n\n");
            for (Map<String, Object> row : traces) {
                String traceId = safeStr(row.get("trace_id"));
                String requestType = safeStr(row.get("request_type"));
                String latestStep = safeStr(row.get("latest_step"));
                String latestStatus = safeStr(row.get("latest_status"));
                String latestDetail = safeStr(row.get("latest_detail"));
                String latestAt = formatTs(row.get("latest_at"));
                String firstAt = formatTs(row.get("first_at"));
                sb.append("[").append(latestAt).append("] ");
                sb.append("trace=").append(traceId);
                sb.append(" type=").append(requestType);
                sb.append(" step=").append(latestStep);
                sb.append(" status=").append(latestStatus);
                if (!latestDetail.isEmpty()) sb.append(" detail=").append(latestDetail);
                if (!firstAt.equals(latestAt)) sb.append(" started=").append(firstAt);
                sb.append("\n");
            }
            sb.append("\n");
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

    private OffsetDateTime since(int hours) {
        return Instant.now().minusSeconds(cappedHours(hours) * 3600L)
            .atOffset(ZoneOffset.UTC);
    }

    private int cappedHours(int hours) {
        return Math.min(Math.max(hours, 1), 24);
    }
}
