package com.omni.panel.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保存和读取异步查询状态及结果快照。
 *
 * <p>快照始终写入本机内存；配置 Redis 且序列化结果未超过大小限制时，同时写入 Redis
 * 并保留一小时。Redis 缺失、不可用、内容无法解析或结果过大时降级为本机存储，因此该
 * 情况下其他应用实例无法读取本实例的查询状态。</p>
 */
@Component
public class QueryStateStore {
    private static final Logger log = LoggerFactory.getLogger(QueryStateStore.class);
    private final ConcurrentHashMap<String, QuerySnapshot> local = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final QueryProperties properties;

    public QueryStateStore(ObjectProvider<StringRedisTemplate> redisProvider, ObjectMapper objectMapper,
                           QueryProperties properties) {
        this.redis = redisProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 保存查询快照，并在条件允许时同步写入 Redis。
     *
     * @param snapshot 要保存的查询状态和结果
     */
    public void save(QuerySnapshot snapshot) {
        local.put(snapshot.queryId(), snapshot);
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            if (redis != null
                && json.getBytes(StandardCharsets.UTF_8).length <= properties.redisResultLimitBytes()) {
                redis.opsForValue().set(key(snapshot.queryId()), json, Duration.ofHours(1));
            }
        } catch (Exception exception) {
            log.warn("Redis 不可用，查询状态使用本机存储");
        }
    }

    /**
     * 优先从本机内存读取查询快照，未命中时再尝试 Redis。
     *
     * @param queryId 查询任务标识
     * @return 查询快照；不存在或 Redis 降级期间本机未命中时返回 {@code null}
     */
    public QuerySnapshot get(String queryId) {
        QuerySnapshot snapshot = local.get(queryId);
        if (snapshot != null) {
            return snapshot;
        }
        try {
            if (redis == null) {
                return null;
            }
            String json = redis.opsForValue().get(key(queryId));
            return json == null ? null : objectMapper.readValue(json, QuerySnapshot.class);
        } catch (JsonProcessingException exception) {
            log.warn("Redis 查询状态内容无法解析");
            return null;
        } catch (Exception exception) {
            log.warn("Redis 不可用，查询状态使用本机存储");
            return null;
        }
    }

    /**
     * 生成 Redis 中存储查询快照的键名。
     *
     * @param queryId 查询任务标识
     * @return Redis 键
     */
    private String key(String queryId) {
        return "omni:query:" + queryId;
    }

    /**
     * 某一时刻的查询任务状态。
     *
     * @param queryId 查询任务标识
     * @param userId 发起查询的用户标识
     * @param sourceId 查询使用的数据源标识
     * @param status 任务状态
     * @param result 成功完成后的查询结果
     * @param error 失败时的错误信息
     * @param startedAtMs 提交时间（毫秒时间戳）
     * @param durationMs 从提交到结束的耗时毫秒；未结束时为空
     */
    public record QuerySnapshot(String queryId,
                                @JsonSerialize(using = ToStringSerializer.class) long userId,
                                @JsonSerialize(using = ToStringSerializer.class) long sourceId, String status,
                                JdbcQueryExecutor.QueryResult result, String error,
                                Long startedAtMs, Long durationMs) {}
}
