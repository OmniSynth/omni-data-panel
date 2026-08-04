ALTER TABLE sys_role
    ADD COLUMN description VARCHAR(500) NULL AFTER name,
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER description,
    ADD COLUMN built_in BOOLEAN NOT NULL DEFAULT FALSE AFTER enabled;

UPDATE sys_role
SET description = '系统内置管理员角色', enabled = TRUE, built_in = TRUE
WHERE code = 'ADMIN';

INSERT INTO sys_permission(code, name) VALUES
    ('query:execute', '执行查询'),
    ('query:raw', '执行原生 SQL'),
    ('dataset:create', '创建数据集'),
    ('chart:create', '创建图表'),
    ('dashboard:create', '创建仪表盘'),
    ('export:execute', '执行导出')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
CROSS JOIN sys_permission permission
WHERE role.code = 'ADMIN';

DROP TABLE bi_resource_permission;

CREATE TABLE bi_role_resource_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    permission VARCHAR(20) NOT NULL,
    UNIQUE KEY uk_role_resource_permission (role_id, resource_type, resource_id),
    KEY idx_role_resource_lookup (resource_type, resource_id, role_id),
    CONSTRAINT fk_role_resource_role
        FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE bi_chart
    MODIFY COLUMN dataset_id BIGINT NULL,
    ADD COLUMN data_source_id BIGINT NULL AFTER dataset_id,
    ADD KEY idx_chart_data_source (data_source_id),
    ADD CONSTRAINT fk_chart_data_source
        FOREIGN KEY (data_source_id) REFERENCES bi_data_source(id);
