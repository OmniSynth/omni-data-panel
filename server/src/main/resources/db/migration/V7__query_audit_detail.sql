ALTER TABLE bi_query_audit
    ADD COLUMN client_ip VARCHAR(64) NULL AFTER sql_text,
    ADD COLUMN user_agent VARCHAR(512) NULL AFTER client_ip,
    ADD COLUMN duration_ms BIGINT NULL AFTER error_message,
    ADD COLUMN result_preview MEDIUMTEXT NULL AFTER duration_ms,
    ADD KEY idx_query_audit_started (started_at);
