package com.example.guardianplants;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LogRetentionRepository {
    private final JdbcTemplate jdbcTemplate;

    public LogRetentionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int pruneAppLogs(int retentionDays) {
        return jdbcTemplate.update(
            "DELETE FROM app_logs WHERE received_at < NOW() - (? || ' days')::INTERVAL",
            retentionDays
        );
    }

    public int pruneChatHistories(int retentionDays) {
        return jdbcTemplate.update(
            "DELETE FROM chat_histories WHERE created_at < NOW() - (? || ' days')::INTERVAL",
            retentionDays
        );
    }
}
