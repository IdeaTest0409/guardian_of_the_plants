-- Request flow tracing table.

CREATE TABLE IF NOT EXISTS request_traces (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    step VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    detail TEXT,
    duration_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_request_traces_trace_id
    ON request_traces(trace_id);

CREATE INDEX IF NOT EXISTS idx_request_traces_created_at
    ON request_traces(created_at);

CREATE INDEX IF NOT EXISTS idx_request_traces_request_type
    ON request_traces(request_type);

CREATE INDEX IF NOT EXISTS idx_request_traces_status
    ON request_traces(status);
