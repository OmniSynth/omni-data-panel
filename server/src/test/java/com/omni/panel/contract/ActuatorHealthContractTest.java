package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ActuatorHealthContractTest {
    @Test
    void 依赖配置安全白名单与Docker探针均指向Actuator健康端点() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertThat(pom).contains("spring-boot-starter-actuator");

        String applicationYml = Files.readString(
                Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertThat(applicationYml).contains(
                "include: health,prometheus",
                "probes:",
                "enabled: true",
                "show-details: never",
                "livenessstate:",
                "readinessstate:",
                "mail:",
                "enabled: false");

        String securityConfig = Files.readString(
                Path.of("src/main/java/com/omni/panel/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);
        assertThat(securityConfig).contains("\"/actuator/health\"", "\"/actuator/health/**\"", "\"/actuator/prometheus\"");

        String dockerfile = Files.readString(Path.of("Dockerfile"), StandardCharsets.UTF_8);
        assertThat(dockerfile).contains(
                "curl",
                "http://127.0.0.1:8080/actuator/health/liveness");
        assertThat(dockerfile).doesNotContain("</dev/tcp/127.0.0.1/8080>");
    }
}
