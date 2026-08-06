package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ObservabilityContractTest {
    @Test
    void 指标依赖暴露与安全白名单齐备() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertThat(pom).contains("micrometer-registry-prometheus");

        String applicationYml = Files.readString(
                Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertThat(applicationYml).contains(
                "include: health,prometheus",
                "metrics-token:",
                "OMNI_METRICS_TOKEN",
                "%X{requestId}");

        String securityConfig = Files.readString(
                Path.of("src/main/java/com/omni/panel/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);
        assertThat(securityConfig).contains("\"/actuator/prometheus\"");
        assertThat(securityConfig).contains("MetricsScrapeFilter");
        assertThat(securityConfig).contains("RequestIdFilter");
    }
}
