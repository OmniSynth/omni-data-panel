CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    UNIQUE KEY uk_sys_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_data_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    jdbc_url VARCHAR(1000) NOT NULL,
    username VARCHAR(128) NOT NULL,
    encrypted_password TEXT NOT NULL,
    owner_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_data_source_owner (owner_id),
    FOREIGN KEY (owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_meta_schema (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    data_source_id BIGINT NOT NULL,
    schema_name VARCHAR(128) NOT NULL,
    UNIQUE KEY uk_meta_schema (data_source_id, schema_name),
    FOREIGN KEY (data_source_id) REFERENCES bi_data_source(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_meta_table (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    data_source_id BIGINT NOT NULL,
    schema_name VARCHAR(128) NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    table_comment VARCHAR(500),
    UNIQUE KEY uk_meta_table (data_source_id, schema_name, table_name),
    FOREIGN KEY (data_source_id, schema_name)
        REFERENCES bi_meta_schema(data_source_id, schema_name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_meta_column (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    data_source_id BIGINT NOT NULL,
    schema_name VARCHAR(128) NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    column_name VARCHAR(128) NOT NULL,
    data_type INT NOT NULL,
    type_name VARCHAR(128) NOT NULL,
    nullable BOOLEAN NOT NULL,
    ordinal_position INT NOT NULL,
    column_comment VARCHAR(500),
    UNIQUE KEY uk_meta_column (data_source_id, schema_name, table_name, column_name),
    FOREIGN KEY (data_source_id, schema_name, table_name)
        REFERENCES bi_meta_table(data_source_id, schema_name, table_name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_dataset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    data_source_id BIGINT NOT NULL,
    schema_name VARCHAR(128) NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    owner_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_dataset_source (data_source_id),
    KEY idx_dataset_owner (owner_id),
    FOREIGN KEY (data_source_id) REFERENCES bi_data_source(id),
    FOREIGN KEY (owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_dataset_field (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    column_name VARCHAR(128) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    aggregation VARCHAR(20),
    UNIQUE KEY uk_dataset_field_name (dataset_id, name),
    UNIQUE KEY uk_dataset_field_column (dataset_id, column_name),
    FOREIGN KEY (dataset_id) REFERENCES bi_dataset(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_chart (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    dataset_id BIGINT NOT NULL,
    query_json JSON NOT NULL,
    chart_type VARCHAR(50) NOT NULL,
    config_json JSON NOT NULL,
    owner_id BIGINT NOT NULL,
    KEY idx_chart_owner (owner_id),
    FOREIGN KEY (dataset_id) REFERENCES bi_dataset(id),
    FOREIGN KEY (owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_dashboard (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    config_json JSON NOT NULL,
    owner_id BIGINT NOT NULL,
    last_refreshed_at DATETIME,
    KEY idx_dashboard_owner (owner_id),
    FOREIGN KEY (owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_dashboard_card (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dashboard_id BIGINT NOT NULL,
    chart_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    layout_json JSON NOT NULL,
    KEY idx_dashboard_card_dashboard (dashboard_id),
    FOREIGN KEY (dashboard_id) REFERENCES bi_dashboard(id) ON DELETE CASCADE,
    FOREIGN KEY (chart_id) REFERENCES bi_chart(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_resource_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission VARCHAR(20) NOT NULL,
    UNIQUE KEY uk_resource_permission (resource_type, resource_id, user_id),
    KEY idx_resource_permission_user (user_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_field_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    allowed BOOLEAN NOT NULL,
    UNIQUE KEY uk_field_permission (dataset_id, user_id, field_name),
    FOREIGN KEY (dataset_id) REFERENCES bi_dataset(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_row_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT,
    name VARCHAR(100) NOT NULL,
    rule_json JSON NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    KEY idx_row_rule_dataset_user (dataset_id, user_id),
    FOREIGN KEY (dataset_id) REFERENCES bi_dataset(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_query_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    query_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    sql_text MEDIUMTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    row_count INT,
    error_message VARCHAR(1000),
    started_at DATETIME NOT NULL,
    finished_at DATETIME,
    UNIQUE KEY uk_query_audit_query (query_id),
    KEY idx_query_audit_user_time (user_id, started_at),
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (data_source_id) REFERENCES bi_data_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    schedule_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    payload_json JSON,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    owner_id BIGINT NOT NULL,
    last_run_at DATETIME,
    KEY idx_schedule_owner (owner_id),
    KEY idx_schedule_enabled (enabled),
    FOREIGN KEY (owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_subscription (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    dashboard_id BIGINT NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    recipients TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    owner_id BIGINT NOT NULL,
    KEY idx_subscription_owner (owner_id),
    FOREIGN KEY (dashboard_id) REFERENCES bi_dashboard(id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_export_task (
    id VARCHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    query_id VARCHAR(36) NOT NULL,
    format VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    object_name VARCHAR(500),
    error_message VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_export_owner_time (owner_id, created_at),
    FOREIGN KEY (owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_user(id, username, password_hash, display_name, enabled)
VALUES (1, 'admin', '$2a$10$4ruqE8FlnERNCuIW/6pI6.1rlZmJiG/plwFwif5KPGxjwbM9Sm6je', '系统管理员', TRUE);
INSERT INTO sys_role(id, code, name) VALUES (1, 'ADMIN', '管理员'), (2, 'USER', '普通用户');
INSERT INTO sys_permission(id, code, name) VALUES
    (1, 'data-source:manage', '管理数据源'),
    (2, 'dataset:manage', '管理数据集'),
    (3, 'dashboard:manage', '管理仪表盘'),
    (4, 'query:execute', '执行查询'),
    (5, 'schedule:manage', '管理调度'),
    (6, 'export:execute', '执行导出'),
    (7, 'query:raw', '执行原生 SQL');
INSERT INTO sys_user_role(user_id, role_id) VALUES (1, 1);
INSERT INTO sys_role_permission(role_id, permission_id) SELECT 1, id FROM sys_permission;
