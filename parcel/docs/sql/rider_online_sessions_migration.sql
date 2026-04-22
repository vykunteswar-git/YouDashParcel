CREATE TABLE IF NOT EXISTS youdash_rider_online_sessions (
    id BIGSERIAL PRIMARY KEY,
    rider_id BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rider_online_sessions_rider
    ON youdash_rider_online_sessions (rider_id);

CREATE INDEX IF NOT EXISTS idx_rider_online_sessions_started_at
    ON youdash_rider_online_sessions (started_at);
