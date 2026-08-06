package com.omni.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisFixedWindowRateLimiterTest {
    @Test
    void 窗口内超过限额后拒绝() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AtomicLong counter = new AtomicLong();
        when(values.increment(anyString())).thenAnswer(invocation -> counter.incrementAndGet());

        RedisFixedWindowRateLimiter limiter = new RedisFixedWindowRateLimiter(redis, Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("auth:1.1.1.1", 2)).isTrue();
        assertThat(limiter.tryAcquire("auth:1.1.1.1", 2)).isTrue();
        assertThat(limiter.tryAcquire("auth:1.1.1.1", 2)).isFalse();
        verify(redis).expire(anyString(), any(Duration.class));
    }
}
