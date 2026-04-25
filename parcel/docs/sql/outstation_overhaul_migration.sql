-- Outstation overhaul migration: timeline events, assignments, and order metadata.

CREATE TABLE IF NOT EXISTS youdash_order_timeline_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NULL,
    event_version INT NOT NULL DEFAULT 1,
    location VARCHAR(255) NULL,
    hub_id BIGINT NULL,
    rider_id BIGINT NULL,
    notes VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_timeline_order ON youdash_order_timeline_events (order_id);
CREATE INDEX idx_order_timeline_status ON youdash_order_timeline_events (status);
CREATE INDEX idx_order_timeline_created_at ON youdash_order_timeline_events (created_at);

CREATE TABLE IF NOT EXISTS youdash_order_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    rider_id BIGINT NOT NULL,
    assignment_role VARCHAR(16) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP NULL
);

CREATE INDEX idx_order_assignments_order ON youdash_order_assignments (order_id);
CREATE INDEX idx_order_assignments_rider ON youdash_order_assignments (rider_id);
CREATE INDEX idx_order_assignments_order_role ON youdash_order_assignments (order_id, assignment_role);

ALTER TABLE youdash_orders
    ADD COLUMN IF NOT EXISTS pickup_rider_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS delivery_rider_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS pickup_otp VARCHAR(6) NULL,
    ADD COLUMN IF NOT EXISTS delivery_otp_generated_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS delivery_otp_attempts INT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimated_delivery_time TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS cutoff_applied BOOLEAN NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_orders_pickup_rider_id ON youdash_orders (pickup_rider_id);
CREATE INDEX IF NOT EXISTS idx_orders_delivery_rider_id ON youdash_orders (delivery_rider_id);
