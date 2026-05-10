CREATE TABLE IF NOT EXISTS server_settings (
    setting_key VARCHAR(120) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
