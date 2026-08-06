package com.omni.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可观测性配置。
 *
 * @param metricsToken Prometheus 刮取令牌；为空时不暴露 /actuator/prometheus
 */
@ConfigurationProperties("omni.observability")
public record ObservabilityProperties(String metricsToken) {
    public ObservabilityProperties {
        if (metricsToken != null && metricsToken.isBlank()) {
            metricsToken = null;
        }
    }

    /** 是否已配置刮取令牌（端点可访问）。 */
    public boolean metricsEnabled() {
        return metricsToken != null && !metricsToken.isBlank();
    }
}
