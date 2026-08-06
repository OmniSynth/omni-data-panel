package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RateLimitSecurityContractTest {
    @Test
    void 配置与安全链声明限流及可嵌入响应头() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertThat(yml).contains(
                "rate-limit:",
                "auth-per-minute:",
                "public-per-minute:",
                "embed-per-minute:",
                "trusted-proxies:",
                "TRUSTED_PROXIES");

        String security = Files.readString(
                Path.of("src/main/java/com/omni/panel/config/SecurityConfig.java"), StandardCharsets.UTF_8);
        assertThat(security).contains(
                "RateLimitFilter",
                "FrameAncestorsFilter",
                "frameOptions",
                "disable",
                "contentTypeOptions",
                "STRICT_ORIGIN_WHEN_CROSS_ORIGIN",
                "permissionsPolicy");

        String filter = Files.readString(
                Path.of("src/main/java/com/omni/panel/config/RateLimitFilter.java"), StandardCharsets.UTF_8);
        assertThat(filter).contains("RateLimiter");

        assertThat(Files.exists(Path.of("src/main/java/com/omni/panel/config/DelegatingRateLimiter.java"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/java/com/omni/panel/config/RedisFixedWindowRateLimiter.java"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/java/com/omni/panel/common/ClientIpResolver.java"))).isTrue();
    }
}
