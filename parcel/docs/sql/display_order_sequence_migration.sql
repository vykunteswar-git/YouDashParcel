-- Short public order IDs: YP-1000, YP-1001, …

CREATE TABLE IF NOT EXISTS youdash_display_order_sequence (
    id BIGINT NOT NULL PRIMARY KEY,
    next_value BIGINT NOT NULL
);

INSERT INTO youdash_display_order_sequence (id, next_value)
SELECT 1, 1000
WHERE NOT EXISTS (SELECT 1 FROM youdash_display_order_sequence WHERE id = 1);

-- Optional: store weight unit on orders (when using ddl-auto=update this is added automatically)
ALTER TABLE youdash_orders
    ADD COLUMN IF NOT EXISTS weight_unit VARCHAR(8) NULL;
