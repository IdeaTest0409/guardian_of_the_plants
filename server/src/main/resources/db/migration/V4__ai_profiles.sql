CREATE TABLE IF NOT EXISTS ai_profiles (
    id VARCHAR(120) PRIMARY KEY,
    label VARCHAR(200) NOT NULL,
    base_url TEXT NOT NULL,
    api_key TEXT,
    model VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_profiles_enabled_sort
    ON ai_profiles (enabled, sort_order, id);
