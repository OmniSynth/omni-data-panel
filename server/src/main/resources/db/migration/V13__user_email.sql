ALTER TABLE sys_user
    ADD COLUMN email VARCHAR(255) NULL AFTER display_name,
    ADD UNIQUE KEY uk_sys_user_email (email);
