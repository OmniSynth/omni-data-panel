INSERT INTO bi_setting(setting_key, setting_value, updated_at)
VALUES ('cache.query.enabled', 'false', NOW()),
       ('cache.query.ttl-seconds', '300', NOW());
