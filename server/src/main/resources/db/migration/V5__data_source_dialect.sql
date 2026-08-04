ALTER TABLE bi_data_source
    ADD COLUMN dialect VARCHAR(32) NOT NULL DEFAULT 'MYSQL' AFTER jdbc_url;

UPDATE bi_data_source
SET dialect = 'MYSQL'
WHERE dialect IS NULL OR dialect = '';
