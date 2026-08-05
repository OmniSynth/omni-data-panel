package com.omni.panel.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.omni.panel.config.SecurityProperties;

/**
 * 登记用户访问会话（jti），并按管理员配置的并发上限淘汰最旧会话。
 */
@Service
public class UserSessionRegistry {
    private static final Logger log = LoggerFactory.getLogger(UserSessionRegistry.class);
    private static final String KEY_PREFIX = "omni:sessions:";

    private final SettingService settingService;
    private final Duration jwtExpiration;
    private final StringRedisTemplate redis;
    private final Map<Long, ConcurrentSkipListMap<Long, String>> localSessions = new ConcurrentHashMap<>();

    /**
     * 注入会话登记所需依赖。
     *
     * @param settingService     系统设置（并发上限）
     * @param securityProperties JWT 过期配置
     * @param redisProvider      Redis 模板；不可用时回退本机存储
     */
    public UserSessionRegistry(SettingService settingService, SecurityProperties securityProperties,
                               ObjectProvider<StringRedisTemplate> redisProvider) {
        this.settingService = settingService;
        this.jwtExpiration = securityProperties.jwtExpiration();
        this.redis = redisProvider.getIfAvailable();
    }

    /**
     * 登记新会话；超出上限时踢掉最旧会话。
     *
     * @param userId    用户标识
     * @param jti       令牌 jti
     * @param expiresAt 过期时间
     */
    public void register(long userId, String jti, Instant expiresAt) {
        int max = settingService.maxConcurrentSessions();
        if (max <= 0 || jti == null || jti.isBlank()) {
            return;
        }
        long score = Instant.now().toEpochMilli();
        long ttlSeconds = Math.max(60, Duration.between(Instant.now(), expiresAt).getSeconds());
        if (redis != null) {
            try {
                registerRedis(userId, jti, score, max, ttlSeconds);
                return;
            } catch (RuntimeException exception) {
                log.warn("Redis 会话登记失败，回退本机存储：{}", exception.getMessage());
            }
        }
        registerLocal(userId, jti, score, max, expiresAt.toEpochMilli());
    }

    /**
     * 判断会话是否仍有效。
     *
     * @param userId 用户标识
     * @param jti    令牌 jti；无并发限制时可为 null
     * @return 仍有效时返回 {@code true}
     */
    public boolean isActive(long userId, String jti) {
        int max = settingService.maxConcurrentSessions();
        if (max <= 0) {
            return true;
        }
        if (jti == null || jti.isBlank()) {
            return false;
        }
        if (redis != null) {
            try {
                Double score = redis.opsForZSet().score(key(userId), jti);
                if (score != null) {
                    return true;
                }
                // Redis 无记录时再查本机，避免短暂切换导致误踢
            } catch (RuntimeException exception) {
                log.warn("Redis 会话校验失败，回退本机存储：{}", exception.getMessage());
            }
        }
        return isActiveLocal(userId, jti);
    }

    /**
     * 在 Redis 有序集合中登记会话并淘汰超限条目。
     *
     * @param userId     用户标识
     * @param jti        令牌 jti
     * @param score      登记时间戳（毫秒，用作分数）
     * @param max        并发上限
     * @param ttlSeconds 键过期秒数
     */
    private void registerRedis(long userId, String jti, long score, int max, long ttlSeconds) {
        String redisKey = key(userId);
        redis.opsForZSet().add(redisKey, jti, score);
        long minAlive = Instant.now().minus(jwtExpiration).toEpochMilli();
        redis.opsForZSet().removeRangeByScore(redisKey, 0, minAlive);
        Long size = redis.opsForZSet().zCard(redisKey);
        if (size != null && size > max) {
            redis.opsForZSet().removeRange(redisKey, 0, size - max - 1);
        }
        redis.expire(redisKey, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 在本机内存中登记会话并淘汰超限条目。
     *
     * @param userId           用户标识
     * @param jti              令牌 jti
     * @param score            登记时间戳（毫秒，用作键）
     * @param max              并发上限
     * @param expiresAtMillis  会话过期时间（毫秒，当前未用于淘汰）
     */
    private void registerLocal(long userId, String jti, long score, int max, long expiresAtMillis) {
        ConcurrentSkipListMap<Long, String> sessions = localSessions.computeIfAbsent(
                userId, ignored -> new ConcurrentSkipListMap<>());
        synchronized (sessions) {
            long minAlive = Instant.now().minus(jwtExpiration).toEpochMilli();
            sessions.headMap(minAlive).clear();
            sessions.values().removeIf(jti::equals);
            // 避免同一毫秒冲突
            while (sessions.containsKey(score)) {
                score++;
            }
            sessions.put(score, jti);
            while (sessions.size() > max) {
                sessions.pollFirstEntry();
            }
        }
    }

    /**
     * 判断 jti 是否仍在本机会话表中。
     *
     * @param userId 用户标识
     * @param jti    令牌 jti
     * @return 仍有效时返回 {@code true}
     */
    private boolean isActiveLocal(long userId, String jti) {
        ConcurrentSkipListMap<Long, String> sessions = localSessions.get(userId);
        if (sessions == null) {
            return false;
        }
        synchronized (sessions) {
            long minAlive = Instant.now().minus(jwtExpiration).toEpochMilli();
            sessions.headMap(minAlive).clear();
            return sessions.containsValue(jti);
        }
    }

    /**
     * 构建 Redis 会话键。
     *
     * @param userId 用户标识
     * @return Redis 键名
     */
    private static String key(long userId) {
        return KEY_PREFIX + userId;
    }

    /**
     * 测试辅助：清空本机会话。
     */
    public void clearLocalForTest() {
        localSessions.clear();
    }

    /**
     * 测试辅助：列出本机某用户会话 jti（按时间升序）。
     */
    public List<String> localJtisForTest(long userId) {
        ConcurrentSkipListMap<Long, String> sessions = localSessions.get(userId);
        if (sessions == null) {
            return List.of();
        }
        synchronized (sessions) {
            return new ArrayList<>(sessions.values());
        }
    }
}
