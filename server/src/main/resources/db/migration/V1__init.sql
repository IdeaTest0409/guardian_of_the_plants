-- Initial schema managed by Flyway for the Spring Boot server.

CREATE TABLE IF NOT EXISTS app_logs (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(120),
    app_version VARCHAR(40),
    severity VARCHAR(20) NOT NULL,
    category VARCHAR(120) NOT NULL,
    message TEXT NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_app_logs_device_id
    ON app_logs(device_id);

CREATE INDEX IF NOT EXISTS idx_app_logs_category
    ON app_logs(category);

CREATE INDEX IF NOT EXISTS idx_app_logs_received_at
    ON app_logs(received_at);

CREATE TABLE IF NOT EXISTS chat_histories (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(120),
    conversation_id VARCHAR(120),
    role VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_histories_device_id
    ON chat_histories(device_id);

CREATE INDEX IF NOT EXISTS idx_chat_histories_conversation_id
    ON chat_histories(conversation_id);

CREATE INDEX IF NOT EXISTS idx_chat_histories_created_at
    ON chat_histories(created_at);
