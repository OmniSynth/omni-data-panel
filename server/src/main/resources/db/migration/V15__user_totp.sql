ALTER TABLE sys_user
    ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER activated_at,
    ADD COLUMN totp_secret VARCHAR(255) NULL AFTER totp_enabled,
    ADD COLUMN totp_pending_secret VARCHAR(255) NULL AFTER totp_secret;

CREATE TABLE sys_user_totp_backup_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    KEY idx_totp_backup_user (user_id),
    CONSTRAINT fk_totp_backup_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
