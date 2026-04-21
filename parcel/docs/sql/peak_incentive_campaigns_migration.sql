-- Optional manual DDL for peak-hour rider incentives

CREATE TABLE IF NOT EXISTS youdash_peak_incentive_campaigns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    service_mode VARCHAR(16),
    bonus_amount DOUBLE NOT NULL,
    min_completed_orders INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from TIMESTAMP(6) NOT NULL,
    valid_to TIMESTAMP(6) NOT NULL,
    days_of_week_csv VARCHAR(128),
    start_time_hhmm VARCHAR(5) NOT NULL,
    end_time_hhmm VARCHAR(5) NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6)
);

CREATE INDEX idx_peak_incentive_active_window
    ON youdash_peak_incentive_campaigns (is_active, valid_from, valid_to);
