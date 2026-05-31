-- Split D2D/H2D orders need TWO financial rows per order (pickup + delivery riders).
-- Early migration had UNIQUE(order_id) via idx_orf_order — that blocks the delivery leg row.
-- Safe to run once: drops the bad index; uk_orf_order_rider (order_id, rider_id) remains.

ALTER TABLE youdash_order_rider_financials DROP INDEX idx_orf_order;
