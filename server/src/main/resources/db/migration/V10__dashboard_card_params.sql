-- MySQL 不允许 TEXT 列带默认值；用 JSON 可空列，再回填 []。
ALTER TABLE bi_dashboard_card
    ADD COLUMN bindings_json JSON NULL AFTER layout_json,
    ADD COLUMN click_action_json JSON NULL AFTER bindings_json;

UPDATE bi_dashboard_card
SET bindings_json = CAST('[]' AS JSON)
WHERE bindings_json IS NULL;
