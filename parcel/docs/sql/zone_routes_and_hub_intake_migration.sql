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

CREATE TABLE IF NOT EXISTS youdash_hub_corridor_sla (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hub_id BIGINT NOT NULL,
    destination_zone_id BIGINT NOT NULL,
    cutoff_time TIME NOT NULL,
    slot_label VARCHAR(80) NULL,
    delivery_type VARCHAR(20) NOT NULL,
    delivery_time TIME NULL,
    delivery_day_offset INT NULL DEFAULT 1,
    delivered_within_hours INT NULL,
    priority INT NOT NULL,
    is_active TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    UNIQUE KEY uk_hub_corridor_sla (hub_id, destination_zone_id, priority)
);

-- Price: 1) hub_routes 2) zone_routes 3) default_route_rate_per_km
-- Timing: 1) hub.intake_cutoff (handover gate)
--         2) hub_corridor_sla (this hub → dest zone)
--         3) zone_route_sla
--         4) hub_route_sla
