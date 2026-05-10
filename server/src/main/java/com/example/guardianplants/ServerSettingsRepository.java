package com.example.guardianplants;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ServerSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public ServerSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> get(String key) {
        return jdbcTemplate.query(
            "SELECT setting_value FROM server_settings WHERE setting_key = ?",
            rs -> rs.next() ? Optional.ofNullable(rs.getString("setting_value")) : Optional.empty(),
            key
        );
    }

    public void set(String key, String value) {
        jdbcTemplate.update(
            """
            INSERT INTO server_settings (setting_key, setting_value, updated_at)
            VALUES (?, ?, NOW())
            ON CONFLICT (setting_key)
            DO UPDATE SET setting_value = EXCLUDED.setting_value, updated_at = NOW()
            """,
            key,
            value
        );
    }
}
