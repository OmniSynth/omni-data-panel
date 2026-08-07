INSERT INTO sys_permission(code, name) VALUES
    ('subscription:manage', '管理邮件订阅')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 已有调度权限的角色保留订阅能力，避免升级后丢失入口
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT rp.role_id, p.id
FROM sys_role_permission rp
JOIN sys_permission old ON old.id = rp.permission_id AND old.code = 'schedule:manage'
JOIN sys_permission p ON p.code = 'subscription:manage';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
CROSS JOIN sys_permission permission
WHERE role.code = 'ADMIN' AND permission.code = 'subscription:manage';
