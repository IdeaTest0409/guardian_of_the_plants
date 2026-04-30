package com.example.guardianplants.controller;

import com.example.guardianplants.LogViewerRepository;
import com.example.guardianplants.service.TtsHealthService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogViewerController {

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
}
