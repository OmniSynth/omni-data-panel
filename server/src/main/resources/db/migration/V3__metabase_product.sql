CREATE TABLE bi_collection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    parent_id BIGINT NULL,
    personal_owner_id BIGINT NULL,
    owner_id BIGINT NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_collection_parent (parent_id),
    KEY idx_collection_owner (owner_id),
    UNIQUE KEY uk_collection_personal_owner (personal_owner_id),
    CONSTRAINT fk_collection_parent FOREIGN KEY (parent_id) REFERENCES bi_collection(id),
    CONSTRAINT fk_collection_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id),
    CONSTRAINT fk_collection_personal_owner FOREIGN KEY (personal_owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_recent_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    visited_at DATETIME NOT NULL,
    UNIQUE KEY uk_recent_user_resource (user_id, resource_type, resource_id),
    KEY idx_recent_user_visited (user_id, visited_at),
    CONSTRAINT fk_recent_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_metric (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    model_id BIGINT NOT NULL,
    expression_json JSON NOT NULL,
    aggregation VARCHAR(20) NOT NULL,
    collection_id BIGINT NULL,
    owner_id BIGINT NOT NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_metric_model (model_id),
    KEY idx_metric_collection (collection_id),
    KEY idx_metric_owner (owner_id),
    CONSTRAINT fk_metric_model FOREIGN KEY (model_id) REFERENCES bi_dataset(id),
    CONSTRAINT fk_metric_collection FOREIGN KEY (collection_id) REFERENCES bi_collection(id),
    CONSTRAINT fk_metric_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_public_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    token VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_public_link_token (token),
    KEY idx_public_link_resource (resource_type, resource_id),
    CONSTRAINT fk_public_link_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_setting (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE bi_dataset
    MODIFY COLUMN schema_name VARCHAR(128) NULL,
    MODIFY COLUMN table_name VARCHAR(128) NULL,
    ADD COLUMN collection_id BIGINT NULL AFTER owner_id,
    ADD COLUMN description VARCHAR(500) NULL AFTER name,
    ADD COLUMN model_type VARCHAR(20) NOT NULL DEFAULT 'TABLE' AFTER description,
    ADD COLUMN definition_sql TEXT NULL AFTER table_name,
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at,
    ADD KEY idx_dataset_collection (collection_id),
    ADD CONSTRAINT fk_dataset_collection FOREIGN KEY (collection_id) REFERENCES bi_collection(id);

ALTER TABLE bi_chart
    ADD COLUMN collection_id BIGINT NULL AFTER owner_id,
    ADD COLUMN description VARCHAR(500) NULL AFTER name,
    ADD COLUMN deleted_at DATETIME NULL,
    ADD COLUMN updated_at DATETIME NULL,
    ADD KEY idx_chart_collection (collection_id),
    ADD CONSTRAINT fk_chart_collection FOREIGN KEY (collection_id) REFERENCES bi_collection(id);

ALTER TABLE bi_dashboard
    ADD COLUMN collection_id BIGINT NULL AFTER owner_id,
    ADD COLUMN description VARCHAR(500) NULL AFTER name,
    ADD COLUMN deleted_at DATETIME NULL,
    ADD COLUMN updated_at DATETIME NULL,
    ADD KEY idx_dashboard_collection (collection_id),
    ADD CONSTRAINT fk_dashboard_collection FOREIGN KEY (collection_id) REFERENCES bi_collection(id);

INSERT INTO bi_collection(name, description, parent_id, personal_owner_id, owner_id, archived, created_at, updated_at)
SELECT '你的个人集合', NULL, NULL, u.id, u.id, FALSE, NOW(), NOW()
FROM sys_user u;

UPDATE bi_dataset d
    INNER JOIN bi_collection c ON c.personal_owner_id = d.owner_id
SET d.collection_id = c.id
WHERE d.collection_id IS NULL;

UPDATE bi_chart ch
    INNER JOIN bi_collection c ON c.personal_owner_id = ch.owner_id
SET ch.collection_id = c.id, ch.updated_at = NOW()
WHERE ch.collection_id IS NULL;

UPDATE bi_dashboard dash
    INNER JOIN bi_collection c ON c.personal_owner_id = dash.owner_id
SET dash.collection_id = c.id, dash.updated_at = NOW()
WHERE dash.collection_id IS NULL;

INSERT INTO bi_setting(setting_key, setting_value, updated_at)
VALUES ('site.name', '全域数据分析', NOW()),
       ('embed.enabled', 'true', NOW());
