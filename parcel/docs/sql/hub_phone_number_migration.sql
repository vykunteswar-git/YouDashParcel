-- Hub contact phone (optional when using ddl-auto=update)

ALTER TABLE youdash_hubs
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(15) NULL;
