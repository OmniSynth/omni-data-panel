package com.omni.panel.config;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 基于 Caffeine 的固定窗口计数限流器（进程内）。
 */
public class FixedWindowRateLimiter implements RateLimiter {
    private final Cache<String, AtomicInteger> counters;
    private final long windowMillis;

    /**
     * @param window 窗口时长
     * @param maxKeys 缓存键上限
     */
    public FixedWindowRateLimiter(Duration window, long maxKeys) {
        this.windowMillis = Math.max(1L, window.toMillis());
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(window.multipliedBy(2))
                .maximumSize(Math.max(1L, maxKeys))
                .build();
    }

    /**
     * 在固定窗口内尝试占用一次配额。
     *
     * @param key   限流键（通常含桶名与 IP）
     * @param limit 窗口内最大次数
     * @return 未超限返回 {@code true}
     */
    @Override
    public boolean tryAcquire(String key, int limit) {
        if (limit < 1) {
            return false;
        }
        long windowId = System.currentTimeMillis() / windowMillis;
        String fullKey = key + ':' + windowId;
        AtomicInteger counter = counters.get(fullKey, ignored -> new AtomicInteger(0));
        return counter.incrementAndGet() <= limit;
    }
}
