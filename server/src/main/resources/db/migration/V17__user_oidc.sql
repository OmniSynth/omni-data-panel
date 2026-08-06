ALTER TABLE sys_user
    ADD COLUMN auth_source VARCHAR(20) NOT NULL DEFAULT 'LOCAL' AFTER totp_pending_secret,
    ADD COLUMN idp_subject VARCHAR(255) NULL AFTER auth_source;

CREATE UNIQUE INDEX uk_sys_user_idp_subject ON sys_user (idp_subject);
