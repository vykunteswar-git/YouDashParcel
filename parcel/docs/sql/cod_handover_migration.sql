-- Optional manual migration when not using spring.jpa.hibernate.ddl-auto=update
-- COD commission handover (Option A): pending tracks commission only, not full COD.

ALTER TABLE youdash_riders
    ADD COLUMN IF NOT EXISTS cod_handover_limit DOUBLE PRECISION DEFAULT 1000;

CREATE TABLE IF NOT EXISTS youdash_cod_deposits (
    id BIGSERIAL PRIMARY KEY,
    rider_id BIGINT NOT NULL,
    hub_id BIGINT,
    amount DOUBLE PRECISION NOT NULL,
    admin_user_id BIGINT,
    note VARCHAR(512),
    created_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_cod_deposit_rider ON youdash_cod_deposits (rider_id);
CREATE INDEX IF NOT EXISTS idx_cod_deposit_created ON youdash_cod_deposits (created_at);

ALTER TABLE youdash_order_rider_financials
    ADD COLUMN IF NOT EXISTS cod_deposit_id BIGINT;
