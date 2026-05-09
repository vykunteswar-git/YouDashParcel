-- Persist outstation quote line items (pre-GST pickup/hub/drop/weight) for rider earnings and API breakdown.
ALTER TABLE youdash_orders ADD COLUMN IF NOT EXISTS outstation_pickup_cost DOUBLE NULL;
ALTER TABLE youdash_orders ADD COLUMN IF NOT EXISTS outstation_hub_cost DOUBLE NULL;
ALTER TABLE youdash_orders ADD COLUMN IF NOT EXISTS outstation_drop_cost DOUBLE NULL;
ALTER TABLE youdash_orders ADD COLUMN IF NOT EXISTS outstation_weight_cost DOUBLE NULL;
