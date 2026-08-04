CREATE TABLE sys_login_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    user_id BIGINT NULL,
    success BOOLEAN NOT NULL,
    message VARCHAR(255) NOT NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    logged_at DATETIME NOT NULL,
    KEY idx_login_audit_time (logged_at),
    KEY idx_login_audit_user_time (username, logged_at),
    KEY idx_login_audit_user_id (user_id),
    CONSTRAINT fk_login_audit_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
