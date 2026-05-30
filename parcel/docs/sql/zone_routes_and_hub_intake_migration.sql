-- Zone corridors + per-hub intake (optional when using ddl-auto=update)

CREATE TABLE IF NOT EXISTS youdash_zone_routes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    origin_zone_id BIGINT NOT NULL,
    destination_zone_id BIGINT NOT NULL,
    rate_per_km DOUBLE NOT NULL,
    is_active TINYINT(1) DEFAULT 1,
    UNIQUE KEY uk_zone_route_pair (origin_zone_id, destination_zone_id)
);

CREATE TABLE IF NOT EXISTS youdash_zone_route_sla (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_route_id BIGINT NOT NULL,
    cutoff_time TIME NULL,
    delivery_type VARCHAR(20) NOT NULL,
    delivery_time TIME NULL,
    delivered_within_hours INT NULL,
    priority INT NOT NULL,
    is_active TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

ALTER TABLE youdash_hubs
    ADD COLUMN IF NOT EXISTS intake_cutoff TIME NULL;

-- Rate resolution order at runtime:
-- 1) youdash_hub_routes (hub pair override)
-- 2) youdash_zone_routes (zone pair)
-- 3) youdash_price_config.default_route_rate_per_km
