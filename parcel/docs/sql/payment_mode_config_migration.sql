-- Optional manual DDL for checkout payment mode controls (when not using ddl-auto update)

ALTER TABLE youdash_price_config
    ADD COLUMN IF NOT EXISTS cod_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS online_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS default_payment_type VARCHAR(16) NOT NULL DEFAULT 'ONLINE';

-- Backfill defaults for existing rows
UPDATE youdash_price_config
SET cod_enabled = COALESCE(cod_enabled, TRUE),
    online_enabled = COALESCE(online_enabled, TRUE),
    default_payment_type = COALESCE(default_payment_type, 'ONLINE');
