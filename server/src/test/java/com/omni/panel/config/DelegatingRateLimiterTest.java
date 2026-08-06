package com.omni.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class DelegatingRateLimiterTest {
    @Test
    void Redis成功时不走本机() {
        RateLimiter redis = mock(RateLimiter.class);
        RateLimiter local = mock(RateLimiter.class);
        when(redis.tryAcquire("k", 1)).thenReturn(true);
        DelegatingRateLimiter limiter = DelegatingRateLimiter.forTests(redis, local);

        assertThat(limiter.tryAcquire("k", 1)).isTrue();
        verify(local, times(0)).tryAcquire(anyString(), anyInt());
    }

    @Test
    void Redis抛错时降级本机() {
        RateLimiter redis = mock(RateLimiter.class);
        RateLimiter local = mock(RateLimiter.class);
        when(redis.tryAcquire("k", 1)).thenThrow(new RuntimeException("redis down"));
        when(local.tryAcquire("k", 1)).thenReturn(true);
        DelegatingRateLimiter limiter = DelegatingRateLimiter.forTests(redis, local);

        assertThat(limiter.tryAcquire("k", 1)).isTrue();
        verify(local).tryAcquire("k", 1);
    }

    @Test
    void 无Redis时直接本机() {
        RateLimiter local = mock(RateLimiter.class);
        when(local.tryAcquire("k", 1)).thenReturn(false);
        DelegatingRateLimiter limiter = DelegatingRateLimiter.forTests(null, local);

        assertThat(limiter.tryAcquire("k", 1)).isFalse();
        verify(local).tryAcquire("k", 1);
    }
}
