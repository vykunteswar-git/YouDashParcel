-- Optional manual DDL for user/rider notification inbox

CREATE TABLE IF NOT EXISTS youdash_notification_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_type VARCHAR(16) NOT NULL,
    recipient_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(1024) NOT NULL,
    notification_type VARCHAR(64),
    data_json TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_notif_recipient_created
    ON youdash_notification_inbox (recipient_type, recipient_id, created_at);
CREATE INDEX idx_notif_unread
    ON youdash_notification_inbox (recipient_type, recipient_id, is_read);
