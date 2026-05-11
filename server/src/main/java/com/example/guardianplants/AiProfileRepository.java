package com.example.guardianplants;

import com.example.guardianplants.dto.AiProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AiProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiProfile> findEnabled() {
        return jdbcTemplate.query(
            """
            SELECT id, label, base_url, api_key, model
            FROM ai_profiles
            WHERE enabled = TRUE
            ORDER BY sort_order ASC, id ASC
            """,
            (rs, rowNum) -> new AiProfile(
                rs.getString("id"),
                rs.getString("label"),
                rs.getString("base_url"),
                rs.getString("api_key"),
                rs.getString("model")
            )
        );
    }

    public Optional<AiProfile> findById(String id) {
        return jdbcTemplate.query(
            """
            SELECT id, label, base_url, api_key, model
            FROM ai_profiles
            WHERE id = ?
            """,
            rs -> rs.next()
                ? Optional.of(new AiProfile(
                    rs.getString("id"),
                    rs.getString("label"),
                    rs.getString("base_url"),
                    rs.getString("api_key"),
                    rs.getString("model")
                ))
                : Optional.empty(),
            id
        );
    }

    public boolean exists(String id) {
        Boolean exists = jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM ai_profiles WHERE id = ?)",
            Boolean.class,
            id
        );
        return Boolean.TRUE.equals(exists);
    }

    public void upsert(AiProfile profile) {
        jdbcTemplate.update(
            """
            INSERT INTO ai_profiles (id, label, base_url, api_key, model, enabled, sort_order, updated_at)
            VALUES (?, ?, ?, ?, ?, TRUE, 100, NOW())
            ON CONFLICT (id)
            DO UPDATE SET
                label = EXCLUDED.label,
                base_url = EXCLUDED.base_url,
                api_key = EXCLUDED.api_key,
                model = EXCLUDED.model,
                enabled = TRUE,
                updated_at = NOW()
            """,
            profile.id(),
            profile.label(),
            profile.baseUrl(),
            profile.apiKey(),
            profile.model()
        );
    }

    public void update(AiProfile profile) {
        jdbcTemplate.update(
            """
            UPDATE ai_profiles
            SET label = ?, base_url = ?, api_key = ?, model = ?, enabled = TRUE, updated_at = NOW()
            WHERE id = ?
            """,
            profile.label(),
            profile.baseUrl(),
            profile.apiKey(),
            profile.model(),
            profile.id()
        );
    }

    public void disable(String id) {
        jdbcTemplate.update(
            "UPDATE ai_profiles SET enabled = FALSE, updated_at = NOW() WHERE id = ?",
            id
        );
    }
}
