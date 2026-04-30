package com.example.guardianplants;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class RequestTraceRepository {

    private final JdbcTemplate jdbcTemplate;

    public RequestTraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertStep(String traceId, String requestType, String step, String status, String detail, Integer durationMs) {
        jdbcTemplate.update(
            """
            INSERT INTO request_traces (trace_id, request_type, step, status, detail, duration_ms)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            traceId, requestType, step, status,
            detail != null && detail.length() > 2000 ? detail.substring(0, 2000) : detail,
            durationMs
        );
    }

    public List<Map<String, Object>> getLatestFlows(int limit) {
        return jdbcTemplate.queryForList(
            """
            SELECT rt.trace_id,
                   rt.request_type,
                   rt.step AS latest_step,
                   rt.status AS latest_status,
                   rt.detail AS latest_detail,
                   rt.duration_ms AS latest_duration,
                   rt.created_at AS latest_at,
                   first_step.created_at AS first_at,
                   COUNT(*) OVER (PARTITION BY rt.trace_id) AS total_steps,
                   MAX(CASE WHEN rt.status = 'error' THEN 1 ELSE 0 END) OVER (PARTITION BY rt.trace_id) AS has_error
            FROM request_traces rt
            INNER JOIN (
                SELECT trace_id, MIN(created_at) AS created_at
                FROM request_traces
                GROUP BY trace_id
            ) first_step ON rt.trace_id = first_step.trace_id
            WHERE rt.step IN ('complete', 'error')
               OR rt.id = (SELECT MAX(id) FROM request_traces rt2 WHERE rt2.trace_id = rt.trace_id)
            ORDER BY rt.created_at DESC
            LIMIT ?
            """,
            limit
        );
    }

    public List<Map<String, Object>> getTraceSteps(String traceId) {
        return jdbcTemplate.queryForList(
            """
            SELECT step, status, detail, duration_ms, created_at
            FROM request_traces
            WHERE trace_id = ?
            ORDER BY created_at ASC
            """,
            traceId
        );
    }

    public List<Map<String, Object>> getRecentTraces(int hours) {
        return jdbcTemplate.queryForList(
            """
            SELECT rt.trace_id,
                   rt.request_type,
                   rt.step AS latest_step,
                   rt.status AS latest_status,
                   rt.detail AS latest_detail,
                   rt.duration_ms AS latest_duration,
                   rt.created_at AS latest_at,
                   first_step.created_at AS first_at
            FROM request_traces rt
            INNER JOIN (
                SELECT trace_id, MAX(created_at) AS created_at
                FROM request_traces
                WHERE created_at >= NOW() - (? || ' hours')::INTERVAL
                GROUP BY trace_id
            ) first_step ON rt.trace_id = first_step.trace_id
            WHERE rt.id = (SELECT MAX(id) FROM request_traces rt2 WHERE rt2.trace_id = rt.trace_id)
            ORDER BY rt.created_at DESC
            """,
            hours
        );
    }
}
