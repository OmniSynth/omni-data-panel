package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RbacMigrationContractTest {
    @Test
    void V2迁移建立角色资源授权并使旧用户授权失效() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V2__role_resource_permissions_and_raw_charts.sql")) {
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains(
                    "ADD COLUMN description", "ADD COLUMN enabled", "ADD COLUMN built_in",
                    "CREATE TABLE bi_role_resource_permission", "UNIQUE KEY uk_role_resource_permission",
                    "FOREIGN KEY (role_id) REFERENCES sys_role(id)", "DROP TABLE bi_resource_permission",
                    "MODIFY COLUMN dataset_id BIGINT NULL", "ADD COLUMN data_source_id BIGINT NULL",
                    "'export:execute'", "WHERE role.code = 'ADMIN'");
        }
    }
}
