package com.omni.panel.config;

/**
 * 固定窗口限流器抽象（本机或 Redis）。
 */
public interface RateLimiter {
    /**
     * 在固定窗口内尝试占用一次配额。
     *
     * @param key   限流键（通常含桶名与 IP）
     * @param limit 窗口内最大次数
     * @return 未超限返回 {@code true}
     */
    boolean tryAcquire(String key, int limit);
}
