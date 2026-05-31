-- Delivery rider confirmed parcel collected from destination hub (OUT_FOR_DELIVERY last mile).
ALTER TABLE youdash_orders
    ADD COLUMN IF NOT EXISTS destination_hub_collected_at TIMESTAMP NULL;
