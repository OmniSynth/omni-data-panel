CREATE TABLE bi_dataset_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NULL,
    dataset_name VARCHAR(200) NOT NULL,
    action VARCHAR(32) NOT NULL,
    operator_id BIGINT NULL,
    detail VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    KEY idx_dataset_audit_time (created_at),
    KEY idx_dataset_audit_dataset_time (dataset_id, created_at),
    KEY idx_dataset_audit_action_time (action, created_at),
    CONSTRAINT fk_dataset_audit_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
