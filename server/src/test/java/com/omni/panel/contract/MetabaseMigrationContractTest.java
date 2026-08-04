package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MetabaseMigrationContractTest {
    @Test
    void V3迁移创建集合最近指标公开链接与设置并扩展内容表() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V3__metabase_product.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains(
                    "CREATE TABLE bi_collection",
                    "personal_owner_id",
                    "CREATE TABLE bi_recent_item",
                    "uk_recent_user_resource",
                    "CREATE TABLE bi_metric",
                    "CREATE TABLE bi_public_link",
                    "CREATE TABLE bi_setting",
                    "model_type VARCHAR(20) NOT NULL DEFAULT 'TABLE'",
                    "definition_sql",
                    "deleted_at",
                    "你的个人集合",
                    "site.name",
                    "全域数据分析",
                    "embed.enabled");
            assertThat(sql).contains("ALTER TABLE bi_dataset", "ALTER TABLE bi_chart", "ALTER TABLE bi_dashboard");
        }
    }
}
