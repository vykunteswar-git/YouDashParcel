-- Optional manual migration when not using Hibernate ddl-auto=update.
-- Adds handover OTP column for INCITY orders (generated on rider accept).

ALTER TABLE youdash_orders
    ADD COLUMN delivery_otp VARCHAR(6) NULL COMMENT 'Delivery handover OTP; API exposes only in IN_TRANSIT';

ALTER TABLE youdash_orders
    ADD COLUMN is_otp_verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Set true after POST /orders/{id}/verify-otp';
