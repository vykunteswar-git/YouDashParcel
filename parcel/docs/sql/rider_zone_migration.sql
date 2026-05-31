-- V1: rider primary service zone (used for pickup/delivery assignment by hub zone).
ALTER TABLE youdash_riders
    ADD COLUMN IF NOT EXISTS zone_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_youdash_riders_zone_id ON youdash_riders (zone_id);
