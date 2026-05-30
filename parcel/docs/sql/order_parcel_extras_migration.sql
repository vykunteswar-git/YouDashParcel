-- Parcel step extras from customer booking (contents, declared value, handling, compliance).
ALTER TABLE youdash_orders
    ADD COLUMN IF NOT EXISTS package_contents VARCHAR(512),
    ADD COLUMN IF NOT EXISTS declared_value DECIMAL(12, 2),
    ADD COLUMN IF NOT EXISTS piece_count INT DEFAULT 1,
    ADD COLUMN IF NOT EXISTS is_fragile TINYINT(1) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS contains_liquid TINYINT(1) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS contains_battery TINYINT(1) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS prohibited_items_accepted TINYINT(1) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS parcel_declaration_accepted TINYINT(1) DEFAULT 0;
