ALTER TABLE bi_data_source
    ADD COLUMN host VARCHAR(255) NULL AFTER name,
    ADD COLUMN port INT NULL AFTER host,
    ADD COLUMN default_database VARCHAR(128) NULL AFTER port;
