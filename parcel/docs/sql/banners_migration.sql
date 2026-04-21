-- Optional manual DDL for banner management APIs

CREATE TABLE IF NOT EXISTS youdash_banners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128),
    subtitle VARCHAR(255),
    image_url VARCHAR(1024) NOT NULL,
    redirect_url VARCHAR(1024),
    sort_order INT DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMP(6),
    ends_at TIMESTAMP(6),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6)
);

CREATE INDEX idx_banners_active_sort ON youdash_banners (is_active, sort_order, id);
