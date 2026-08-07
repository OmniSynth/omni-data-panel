ALTER TABLE bi_public_link
    ADD COLUMN expires_at DATETIME NULL AFTER created_at;
