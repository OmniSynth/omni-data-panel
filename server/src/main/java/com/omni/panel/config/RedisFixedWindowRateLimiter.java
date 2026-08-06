package com.omni.panel.config;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis INCR 的固定窗口限流器（多实例共享配额）。
 */
public class RedisFixedWindowRateLimiter implements RateLimiter {
    private static final String KEY_PREFIX = "omni:rl:";

    private final StringRedisTemplate redis;
    private final long windowMillis;
    private final Duration keyTtl;

    /**
     * @param redis  Redis 模板
     * @param window 窗口时长
     */
    public RedisFixedWindowRateLimiter(StringRedisTemplate redis, Duration window) {
        this.redis = redis;
        this.windowMillis = Math.max(1L, window.toMillis());
        this.keyTtl = window.multipliedBy(2);
    }

    /**
     * 在当前固定窗口内递增计数并判断是否未超限。
     *
     * @param key   限流键
     * @param limit 窗口内允许的最大请求数
     * @return 未超限时为 true；limit 小于 1 或 Redis 异常时为 false
     */
    @Override
    public boolean tryAcquire(String key, int limit) {
        if (limit < 1) {
            return false;
        }
        long windowId = System.currentTimeMillis() / windowMillis;
        String fullKey = KEY_PREFIX + key + ':' + windowId;
        Long count = redis.opsForValue().increment(fullKey);
        if (count == null) {
            return false;
        }
        if (count == 1L) {
            redis.expire(fullKey, keyTtl);
        }
        return count <= limit;
    }
}
