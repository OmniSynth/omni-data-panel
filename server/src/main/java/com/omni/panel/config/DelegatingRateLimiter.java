package com.omni.panel.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 优先的限流器；不可用或调用失败时降级为本机固定窗口。
 */
@Primary
@Component
public class DelegatingRateLimiter implements RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(DelegatingRateLimiter.class);

    private final RateLimiter local;
    private final RateLimiter redisLimiter;

    /**
     * @param redisProvider Redis 模板；未配置时仅用本机
     */
    @Autowired
    public DelegatingRateLimiter(ObjectProvider<StringRedisTemplate> redisProvider) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        Duration window = Duration.ofMinutes(1);
        this.local = new FixedWindowRateLimiter(window, 100_000);
        this.redisLimiter = redis == null ? null : new RedisFixedWindowRateLimiter(redis, window);
    }

    private DelegatingRateLimiter(RateLimiter redisLimiter, RateLimiter local) {
        this.redisLimiter = redisLimiter;
        this.local = local;
    }

    /**
     * 单测用：指定 Redis / 本机实现，不经 Spring 构造注入。
     */
    static DelegatingRateLimiter forTests(RateLimiter redisLimiter, RateLimiter local) {
        return new DelegatingRateLimiter(redisLimiter, local);
    }

    @Override
    public boolean tryAcquire(String key, int limit) {
        if (redisLimiter != null) {
            try {
                return redisLimiter.tryAcquire(key, limit);
            } catch (RuntimeException exception) {
                log.warn("Redis 限流失败，降级本机：{}", exception.getMessage());
            }
        }
        return local.tryAcquire(key, limit);
    }
}
