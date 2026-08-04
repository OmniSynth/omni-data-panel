CREATE TABLE bi_role_table_deny (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    schema_name VARCHAR(200) NOT NULL,
    table_name VARCHAR(200) NOT NULL,
    UNIQUE KEY uk_role_table_deny (role_id, data_source_id, schema_name, table_name),
    KEY idx_table_deny_user_lookup (data_source_id, role_id),
    CONSTRAINT fk_table_deny_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_table_deny_source FOREIGN KEY (data_source_id) REFERENCES bi_data_source(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_role_column_deny (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    schema_name VARCHAR(200) NOT NULL,
    table_name VARCHAR(200) NOT NULL,
    column_name VARCHAR(200) NOT NULL,
    UNIQUE KEY uk_role_column_deny (role_id, data_source_id, schema_name, table_name, column_name),
    KEY idx_column_deny_user_lookup (data_source_id, role_id),
    CONSTRAINT fk_column_deny_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_column_deny_source FOREIGN KEY (data_source_id) REFERENCES bi_data_source(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
