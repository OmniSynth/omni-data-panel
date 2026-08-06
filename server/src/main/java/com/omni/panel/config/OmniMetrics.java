package com.omni.panel.config;

import java.time.Duration;
import java.util.Locale;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 业务侧 Micrometer 指标封装（Prometheus 下为 omni_* 命名）。
 */
@Component
public class OmniMetrics {
    private final MeterRegistry registry;

    public OmniMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录登录结果。
     *
     * @param result success / failure / mfa_required
     */
    public void authLogin(String result) {
        registry.counter("omni.auth.login", "result", normalize(result)).increment();
    }

    /**
     * 记录限流触发。
     *
     * @param bucket auth / public / embed
     */
    public void rateLimited(String bucket) {
        registry.counter("omni.http.rate_limited", "bucket", normalize(bucket)).increment();
    }

    /** 记录查询提交。 */
    public void querySubmit() {
        registry.counter("omni.query.submit").increment();
    }

    /**
     * 记录查询完成与耗时。
     *
     * @param status      SUCCEEDED / FAILED / CANCELLED
     * @param startedAtMs 提交时间毫秒戳
     */
    public void queryComplete(String status, long startedAtMs) {
        String normalized = normalize(status);
        registry.counter("omni.query.complete", "status", normalized).increment();
        Timer.builder("omni.query.duration")
                .tag("status", normalized)
                .register(registry)
                .record(Duration.ofMillis(Math.max(0L, System.currentTimeMillis() - startedAtMs)));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
