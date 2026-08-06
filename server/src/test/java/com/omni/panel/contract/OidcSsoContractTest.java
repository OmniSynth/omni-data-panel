package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OidcSsoContractTest {
    @Test
    void 迁移与配置声明OIDC字段() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V17__user_oidc.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("auth_source", "idp_subject", "uk_sys_user_idp_subject");
        }

        String yml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertThat(yml).contains(
                "oidc:",
                "OIDC_ENABLED",
                "OIDC_ISSUER_URI",
                "forward-headers-strategy: framework");

        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertThat(pom).contains("spring-boot-starter-oauth2-client");
    }
}
