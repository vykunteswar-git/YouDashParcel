CREATE TABLE IF NOT EXISTS youdash_order_address_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    coordinate_key VARCHAR(64) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    address VARCHAR(512),
    tag VARCHAR(32),
    door_no VARCHAR(64),
    landmark VARCHAR(255),
    contact_name VARCHAR(128),
    contact_phone VARCHAR(32),
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_order_addr_pref_user_role_coord
    ON youdash_order_address_preferences (user_id, role, coordinate_key);

CREATE INDEX IF NOT EXISTS idx_order_addr_pref_user
    ON youdash_order_address_preferences (user_id);
