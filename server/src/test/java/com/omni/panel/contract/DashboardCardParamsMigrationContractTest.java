package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class DashboardCardParamsMigrationContractTest {
    @Test
    void V10迁移为卡片增加绑定与点击动作字段() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V10__dashboard_card_params.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains(
                    "bi_dashboard_card",
                    "bindings_json",
                    "click_action_json");
            assertThat(sql).doesNotContain("TEXT NOT NULL DEFAULT");
        }
    }
}
