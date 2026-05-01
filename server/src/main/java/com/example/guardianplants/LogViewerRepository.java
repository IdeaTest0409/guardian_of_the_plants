package com.example.guardianplants;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class LogViewerRepository {

    private final JdbcTemplate jdbcTemplate;

    public LogViewerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getChatLogs(int limit) {
        return jdbcTemplate.queryForList(
            """
            SELECT id, created_at, device_id, conversation_id, role,
                   left(content, 200) as content_preview,
                   metadata->>'status' as status,
                   metadata->>'model' as model
            FROM chat_histories
            ORDER BY created_at DESC
            LIMIT ?
            """,
            limit
        );
    }

    public List<Map<String, Object>> getChatLogsSince(OffsetDateTime since, int limit) {
        return jdbcTemplate.queryForList(
            """
            SELECT id, created_at, device_id, conversation_id, role,
                   left(content, 200) as content_preview,
                   metadata->>'status' as status,
                   metadata->>'model' as model
            FROM chat_histories
            WHERE created_at >= ?
            ORDER BY created_at DESC
            LIMIT ?
            """,
            since,
            limit
        );
    }

    public List<Map<String, Object>> getAppLogs(int limit) {
        return jdbcTemplate.queryForList(
            """
            SELECT id, received_at, device_id, app_version, severity,
                   category, left(message, 200) as message_preview
            FROM app_logs
            ORDER BY received_at DESC
            LIMIT ?
            """,
            limit
        );
    }

    public List<Map<String, Object>> getAppLogsSince(OffsetDateTime since, int limit) {
        return jdbcTemplate.queryForList(
            """
            SELECT id, received_at, device_id, app_version, severity,
                   category, left(message, 200) as message_preview
            FROM app_logs
            WHERE received_at >= ?
            ORDER BY received_at DESC
            LIMIT ?
            """,
            since,
            limit
        );
    }

    public List<Map<String, Object>> getChatLogsSince(OffsetDateTime since) {
        return jdbcTemplate.queryForList(
            """
            SELECT created_at, device_id, conversation_id, role,
                   left(content, 500) as content_preview,
                   metadata->>'status' as status,
                   metadata->>'model' as model
            FROM chat_histories
            WHERE created_at >= ?
            ORDER BY created_at ASC
            """,
            since
        );
    }

    public List<Map<String, Object>> getAppLogsSince(OffsetDateTime since) {
        return jdbcTemplate.queryForList(
            """
            SELECT received_at, device_id, app_version, severity,
                   category, left(message, 500) as message_preview,
                   details
            FROM app_logs
            WHERE received_at >= ?
            ORDER BY received_at ASC
            """,
            since
        );
    }

    @Transactional
    public Map<String, Object> deleteAllDisplayedLogs() {
        int requestTraceRows = jdbcTemplate.update("DELETE FROM request_traces");
        int chatHistoryRows = jdbcTemplate.update("DELETE FROM chat_histories");
        int appLogRows = jdbcTemplate.update("DELETE FROM app_logs");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestTraceRows", requestTraceRows);
        result.put("chatHistoryRows", chatHistoryRows);
        result.put("appLogRows", appLogRows);
        result.put("totalRows", requestTraceRows + chatHistoryRows + appLogRows);
        return result;
    }
}
