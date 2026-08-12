CREATE TABLE bi_dashboard_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    dashboard_id BIGINT NULL COMMENT '仪表盘 ID（永久删除后可空）',
    dashboard_name VARCHAR(200) NOT NULL COMMENT '操作时仪表盘名称快照',
    action VARCHAR(32) NOT NULL COMMENT '操作类型：CREATE/UPDATE/SOFT_DELETE/RESTORE/PURGE/CARD_*',
    operator_id BIGINT NULL COMMENT '操作人用户 ID',
    detail VARCHAR(1000) NULL COMMENT '变更摘要',
    created_at DATETIME NOT NULL COMMENT '记录时间',
    KEY idx_dashboard_audit_time (created_at),
    KEY idx_dashboard_audit_dashboard_time (dashboard_id, created_at),
    KEY idx_dashboard_audit_action_time (action, created_at),
    CONSTRAINT fk_dashboard_audit_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表盘变更审计';
