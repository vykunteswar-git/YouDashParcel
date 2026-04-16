-- Rider wallet + ledger + withdrawals + commission config + per-order financial audit row
-- MySQL / MariaDB compatible DDL (run manually on your DB)

CREATE TABLE IF NOT EXISTS youdash_rider_wallets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rider_id BIGINT NOT NULL UNIQUE,
  current_balance DOUBLE NOT NULL DEFAULT 0,
  total_earnings DOUBLE NOT NULL DEFAULT 0,
  total_withdrawn DOUBLE NOT NULL DEFAULT 0,
  cod_pending_amount DOUBLE NOT NULL DEFAULT 0,
  withdrawal_pending_amount DOUBLE NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NULL,
  updated_at TIMESTAMP(3) NULL,
  INDEX idx_rider_wallet_rider (rider_id)
);

CREATE TABLE IF NOT EXISTS youdash_rider_wallet_transactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rider_id BIGINT NOT NULL,
  type VARCHAR(16) NOT NULL,
  amount DOUBLE NOT NULL,
  reference_type VARCHAR(24) NOT NULL,
  reference_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  note VARCHAR(512) NULL,
  metadata_json TEXT NULL,
  created_at TIMESTAMP(3) NULL,
  INDEX idx_wallet_txn_rider_created (rider_id, created_at),
  INDEX idx_wallet_txn_ref (reference_type, reference_id)
);

CREATE TABLE IF NOT EXISTS youdash_rider_withdrawals (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rider_id BIGINT NOT NULL,
  amount DOUBLE NOT NULL,
  status VARCHAR(16) NOT NULL,
  bank_account_name VARCHAR(128) NULL,
  bank_account_number VARCHAR(64) NULL,
  bank_ifsc VARCHAR(32) NULL,
  created_at TIMESTAMP(3) NULL,
  updated_at TIMESTAMP(3) NULL,
  INDEX idx_withdraw_rider_created (rider_id, created_at),
  INDEX idx_withdraw_status (status)
);

CREATE TABLE IF NOT EXISTS youdash_rider_commission_config (
  id BIGINT PRIMARY KEY,
  online_commission_percent DOUBLE NULL,
  cod_cash_commission_percent DOUBLE NULL,
  cod_qr_commission_percent DOUBLE NULL,
  peak_surge_bonus_flat DOUBLE NULL,
  base_fee DOUBLE NULL,
  per_km_rate DOUBLE NULL,
  updated_at TIMESTAMP(3) NULL
);

INSERT INTO youdash_rider_commission_config (id, online_commission_percent, cod_cash_commission_percent, cod_qr_commission_percent, peak_surge_bonus_flat, base_fee, per_km_rate, updated_at)
VALUES (1, 10, 10, 8, 0, 1, 1, CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE id = id;

CREATE TABLE IF NOT EXISTS youdash_order_rider_financials (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL UNIQUE,
  rider_id BIGINT NOT NULL,
  order_amount DOUBLE NOT NULL,
  commission_percent_applied DOUBLE NULL,
  commission_amount DOUBLE NOT NULL,
  surge_bonus_amount DOUBLE NOT NULL,
  rider_earning_amount DOUBLE NOT NULL,
  cod_collected_amount DOUBLE NULL,
  cod_collection_mode VARCHAR(8) NULL,
  cod_settlement_status VARCHAR(16) NULL,
  settled_at TIMESTAMP(3) NULL,
  created_at TIMESTAMP(3) NULL,
  INDEX idx_orf_rider (rider_id)
);

CREATE TABLE IF NOT EXISTS youdash_fin_audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  action VARCHAR(64) NOT NULL,
  actor_type VARCHAR(16) NULL,
  actor_id BIGINT NULL,
  entity_type VARCHAR(32) NULL,
  entity_id BIGINT NULL,
  payload_json TEXT NULL,
  created_at TIMESTAMP(3) NULL,
  INDEX idx_fin_audit_created (created_at),
  INDEX idx_fin_audit_actor (actor_type, actor_id)
);

-- Order COD tracking fields (nullable for backward compatibility)
-- NOTE: If columns already exist, skip these ALTERs (MySQL versions differ on IF NOT EXISTS support).
ALTER TABLE youdash_orders ADD COLUMN cod_collected_amount DOUBLE NULL;
ALTER TABLE youdash_orders ADD COLUMN cod_collection_mode VARCHAR(8) NULL;
ALTER TABLE youdash_orders ADD COLUMN cod_settlement_status VARCHAR(16) NULL;
ALTER TABLE youdash_orders ADD COLUMN distance_km DOUBLE NULL;

-- If you created youdash_rider_commission_config before base_fee/per_km_rate existed, run once:
-- ALTER TABLE youdash_rider_commission_config ADD COLUMN base_fee DOUBLE NULL;
-- ALTER TABLE youdash_rider_commission_config ADD COLUMN per_km_rate DOUBLE NULL;

-- Withdrawal accounting change: new requests no longer reduce current_balance until admin approval.
-- If you had PENDING withdrawals created under the old logic (balance already reduced at request),
-- run ONCE before riders use the new approve path (example — adjust table names if needed):
-- UPDATE youdash_rider_wallets w
-- INNER JOIN youdash_rider_withdrawals r ON r.rider_id = w.rider_id AND r.status = 'PENDING'
-- SET w.current_balance = w.current_balance + r.amount;
