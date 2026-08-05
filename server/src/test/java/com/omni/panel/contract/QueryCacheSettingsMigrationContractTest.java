package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class QueryCacheSettingsMigrationContractTest {
    @Test
    void V9迁移写入查询缓存默认设置() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V9__query_cache_settings.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains(
                    "cache.query.enabled",
                    "cache.query.ttl-seconds",
                    "'false'",
                    "'300'");
        }
    }
}
