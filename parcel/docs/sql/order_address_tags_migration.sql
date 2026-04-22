-- Optional manual DDL for pickup/drop address text + tags

ALTER TABLE youdash_orders
    ADD COLUMN IF NOT EXISTS pickup_address VARCHAR(512),
    ADD COLUMN IF NOT EXISTS pickup_tag VARCHAR(32),
    ADD COLUMN IF NOT EXISTS drop_address VARCHAR(512),
    ADD COLUMN IF NOT EXISTS drop_tag VARCHAR(32);
