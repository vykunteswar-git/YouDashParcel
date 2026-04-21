-- Optional manual DDL for rider ratings feature

CREATE TABLE IF NOT EXISTS youdash_rider_ratings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    rider_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    stars INT NOT NULL,
    compliments_csv VARCHAR(512),
    comment VARCHAR(512),
    created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_rider_rating_rider_created ON youdash_rider_ratings (rider_id, created_at);
CREATE INDEX idx_rider_rating_user_created ON youdash_rider_ratings (user_id, created_at);
