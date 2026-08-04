package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MetaColumnDetailsMigrationContractTest {
    @Test
    void V4迁移补充字段长度主键与外键信息() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V4__meta_column_details.sql")) {
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains(
                    "ADD COLUMN column_size", "ADD COLUMN decimal_digits",
                    "ADD COLUMN primary_key", "ADD COLUMN foreign_key",
                    "ADD COLUMN fk_table_name", "ADD COLUMN fk_column_name");
        }
    }
}
