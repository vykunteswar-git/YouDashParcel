ALTER TABLE youdash_peak_incentive_campaigns
    ADD COLUMN IF NOT EXISTS incentive_type VARCHAR(32) DEFAULT 'DAILY_DELIVERIES_SLOT',
    ADD COLUMN IF NOT EXISTS incentive_date DATE,
    ADD COLUMN IF NOT EXISTS target_online_minutes INT,
    ADD COLUMN IF NOT EXISTS slabs_json TEXT;

UPDATE youdash_peak_incentive_campaigns
SET incentive_type = 'DAILY_DELIVERIES_SLOT'
WHERE incentive_type IS NULL;
