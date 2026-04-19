-- Optional manual DDL when not using spring.jpa.hibernate.ddl-auto=update

CREATE TABLE IF NOT EXISTS youdash_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    discount_type VARCHAR(16) NOT NULL,
    discount_value DOUBLE NOT NULL,
    max_discount_amount DOUBLE,
    min_order_amount DOUBLE,
    valid_from TIMESTAMP(6) NOT NULL,
    valid_to TIMESTAMP(6) NOT NULL,
    max_redemptions_total INT,
    redemption_count INT NOT NULL DEFAULT 0,
    max_redemptions_per_user INT NOT NULL DEFAULT 1,
    service_mode VARCHAR(16),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS youdash_coupon_redemptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP(6),
    CONSTRAINT fk_coupon_redemption_coupon FOREIGN KEY (coupon_id) REFERENCES youdash_coupons (id),
    INDEX idx_coupon_redemption_coupon_user (coupon_id, user_id)
);

ALTER TABLE youdash_orders ADD COLUMN IF NOT EXISTS applied_coupon_code VARCHAR(32);
