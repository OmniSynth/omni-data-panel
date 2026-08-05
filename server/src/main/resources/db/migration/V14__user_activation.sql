ALTER TABLE sys_user
    ADD COLUMN activated BOOLEAN NOT NULL DEFAULT TRUE AFTER enabled,
    ADD COLUMN activated_at DATETIME NULL AFTER activated;

UPDATE sys_user
SET activated = TRUE,
    activated_at = COALESCE(created_at, NOW())
WHERE activated_at IS NULL;

CREATE TABLE sys_user_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_sys_user_token_hash (token_hash),
    KEY idx_sys_user_token_user_purpose (user_id, purpose),
    CONSTRAINT fk_sys_user_token_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
