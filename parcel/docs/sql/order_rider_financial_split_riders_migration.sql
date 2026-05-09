-- Allow one financial row per (order_id, rider_id) for OUTSTATION first-mile + last-mile riders.
-- Run after youdash_order_rider_financials exists (see rider_wallet_migration.sql).

ALTER TABLE youdash_order_rider_financials
  DROP INDEX idx_orf_order;

CREATE UNIQUE INDEX uk_orf_order_rider ON youdash_order_rider_financials (order_id, rider_id);
