-- Optional manual migration when not using spring.jpa.hibernate.ddl-auto=update

ALTER TABLE youdash_price_config
    ADD COLUMN IF NOT EXISTS incity_platform_fee DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS outstation_platform_fee DOUBLE PRECISION;

UPDATE youdash_price_config
SET incity_platform_fee = COALESCE(incity_platform_fee, platform_fee),
    outstation_platform_fee = COALESCE(outstation_platform_fee, platform_fee)
WHERE id = 1;

CREATE TABLE IF NOT EXISTS youdash_outstation_leg_rate_tier (
    id BIGSERIAL PRIMARY KEY,
    leg_type VARCHAR(16) NOT NULL,
    min_weight_kg DOUBLE PRECISION NOT NULL,
    max_weight_kg DOUBLE PRECISION NOT NULL,
    rate_per_km DOUBLE PRECISION NOT NULL,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE
);

-- Seed example tiers from legacy flat rates (run once; skip if rows exist)
-- INSERT INTO youdash_outstation_leg_rate_tier (leg_type, min_weight_kg, max_weight_kg, rate_per_km, sort_order)
-- SELECT 'PICKUP', 0, 10, pickup_rate_per_km, 0 FROM youdash_price_config WHERE id = 1;
