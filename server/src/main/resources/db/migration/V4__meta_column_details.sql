ALTER TABLE bi_meta_column
    ADD COLUMN column_size INT NULL AFTER type_name,
    ADD COLUMN decimal_digits INT NULL AFTER column_size,
    ADD COLUMN primary_key BOOLEAN NOT NULL DEFAULT FALSE AFTER nullable,
    ADD COLUMN foreign_key BOOLEAN NOT NULL DEFAULT FALSE AFTER primary_key,
    ADD COLUMN fk_table_name VARCHAR(128) NULL AFTER foreign_key,
    ADD COLUMN fk_column_name VARCHAR(128) NULL AFTER fk_table_name;
