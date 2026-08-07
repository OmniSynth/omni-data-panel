CREATE TABLE bi_export_audit
(
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    query_id        VARCHAR(36)  NULL,
    data_source_id  BIGINT       NULL,
    format          VARCHAR(16)  NOT NULL,
    mode            VARCHAR(16)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    row_count       INT          NULL,
    byte_size       BIGINT       NULL,
    task_id         VARCHAR(36)  NULL,
    client_ip       VARCHAR(64)  NULL,
    user_agent      VARCHAR(512) NULL,
    error_message   VARCHAR(1000) NULL,
    created_at      DATETIME     NOT NULL,
    KEY idx_export_audit_time (created_at),
    KEY idx_export_audit_user_time (user_id, created_at),
    KEY idx_export_audit_query_time (query_id, created_at),
    CONSTRAINT fk_export_audit_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
