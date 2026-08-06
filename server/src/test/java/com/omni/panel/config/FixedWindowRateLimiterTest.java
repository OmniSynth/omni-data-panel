package com.omni.panel.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {
    @Test
    void 窗口内超过限额后拒绝() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Duration.ofMinutes(1), 1000);
        assertThat(limiter.tryAcquire("auth:1.1.1.1", 3)).isTrue();
        assertThat(limiter.tryAcquire("auth:1.1.1.1", 3)).isTrue();
        assertThat(limiter.tryAcquire("auth:1.1.1.1", 3)).isTrue();
        assertThat(limiter.tryAcquire("auth:1.1.1.1", 3)).isFalse();
    }

    @Test
    void 不同键互相独立() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Duration.ofMinutes(1), 1000);
        assertThat(limiter.tryAcquire("auth:a", 1)).isTrue();
        assertThat(limiter.tryAcquire("auth:a", 1)).isFalse();
        assertThat(limiter.tryAcquire("auth:b", 1)).isTrue();
    }

    @Test
    void 短窗口结束后可再次获取() throws Exception {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Duration.ofMillis(50), 1000);
        assertThat(limiter.tryAcquire("k", 1)).isTrue();
        assertThat(limiter.tryAcquire("k", 1)).isFalse();
        Thread.sleep(60);
        assertThat(limiter.tryAcquire("k", 1)).isTrue();
    }
}
