package com.omni.panel.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.mapper.DataPolicyMapper;
import com.omni.panel.query.JdbcQueryExecutor;
import com.omni.panel.query.QueryProperties;

/**
 * 已保存图表的查询结果缓存，供仪表盘渲染与公开图表复用。
 *
 * <p>开关与 TTL 来自通用设置；键包含图表定义、所有者、数据策略与参数指纹。
 * Redis 不可用或结果过大时跳过写入，不影响实时查询。</p>
 */
@Component
public class ChartResultCache {
    private static final Logger log = LoggerFactory.getLogger(ChartResultCache.class);
    private static final String KEY_PREFIX = "omni:chart-result:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SettingService settingService;
    private final QueryProperties properties;
    private final DataPolicyMapper dataPolicyMapper;

    public ChartResultCache(ObjectProvider<StringRedisTemplate> redisProvider, ObjectMapper objectMapper,
                            SettingService settingService, QueryProperties properties,
                            DataPolicyMapper dataPolicyMapper) {
        this.redis = redisProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.settingService = settingService;
        this.properties = properties;
        this.dataPolicyMapper = dataPolicyMapper;
    }

    /**
     * 读取无参数指纹的缓存（兼容旧调用）。
     *
     * @param chart 图表实体
     * @return 命中时的结果
     */
    public Optional<JdbcQueryExecutor.QueryResult> get(ChartEntity chart) {
        return get(chart, "[]", Map.of());
    }

    /**
     * 读取含参数指纹的缓存。
     *
     * @param chart           图表实体
     * @param bindingsJson    卡片绑定
     * @param parameterValues 运行时参数
     * @return 命中时的结果
     */
    public Optional<JdbcQueryExecutor.QueryResult> get(ChartEntity chart, String bindingsJson,
                                                       Map<String, Object> parameterValues) {
        if (!settingService.queryCacheEnabled() || redis == null || chart == null) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key(chart, bindingsJson, parameterValues));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, JdbcQueryExecutor.QueryResult.class));
        } catch (JsonProcessingException exception) {
            log.warn("查询结果缓存内容无法解析");
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Redis 不可用，跳过查询结果缓存读取");
            return Optional.empty();
        }
    }

    /**
     * 写入无参数指纹的缓存（兼容旧调用）。
     *
     * @param chart  图表实体
     * @param result 查询结果
     */
    public void put(ChartEntity chart, JdbcQueryExecutor.QueryResult result) {
        put(chart, "[]", Map.of(), result);
    }

    /**
     * 写入含参数指纹的缓存。
     *
     * @param chart           图表实体
     * @param bindingsJson    卡片绑定
     * @param parameterValues 运行时参数
     * @param result          查询结果
     */
    public void put(ChartEntity chart, String bindingsJson, Map<String, Object> parameterValues,
                    JdbcQueryExecutor.QueryResult result) {
        if (!settingService.queryCacheEnabled() || redis == null || chart == null || result == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(result);
            if (json.getBytes(StandardCharsets.UTF_8).length > properties.redisResultLimitBytes()) {
                return;
            }
            redis.opsForValue().set(key(chart, bindingsJson, parameterValues), json,
                    Duration.ofSeconds(settingService.queryCacheTtlSeconds()));
        } catch (Exception exception) {
            log.warn("Redis 不可用，跳过查询结果缓存写入");
        }
    }

    /**
     * 生成无参数绑定场景下的缓存键。
     *
     * @param chart 图表实体
     * @return Redis 缓存键
     */
    public String key(ChartEntity chart) {
        return key(chart, "[]", Map.of());
    }

    /**
     * 生成含绑定与参数指纹的缓存键。
     *
     * @param chart           图表实体
     * @param bindingsJson    卡片绑定 JSON
     * @param parameterValues 运行时参数
     * @return Redis 缓存键
     */
    public String key(ChartEntity chart, String bindingsJson, Map<String, Object> parameterValues) {
        String paramsFingerprint;
        try {
            Map<String, Object> sorted = new TreeMap<>();
            if (parameterValues != null) {
                sorted.putAll(parameterValues);
            }
            paramsFingerprint = objectMapper.writeValueAsString(sorted);
        } catch (JsonProcessingException exception) {
            paramsFingerprint = String.valueOf(parameterValues);
        }
        String material = chart.getId() + "|"
                + chart.getOwnerId() + "|"
                + nullToEmpty(chart.getQueryJson()) + "|"
                + (chart.getUpdatedAt() == null ? "" : chart.getUpdatedAt()) + "|"
                + policyFingerprint(chart.getDatasetId(), chart.getOwnerId()) + "|"
                + nullToEmpty(bindingsJson) + "|"
                + paramsFingerprint;
        return KEY_PREFIX + sha256(material);
    }

    /**
     * 汇总模型行/字段策略指纹，供缓存键失效。
     *
     * @param datasetId 模型标识
     * @param ownerId   图表所有者
     * @return 策略指纹；原生 SQL 图表返回 {@code raw}
     */
    private String policyFingerprint(Long datasetId, Long ownerId) {
        if (datasetId == null || ownerId == null) {
            return "raw";
        }
        int fieldRuleCount = dataPolicyMapper.fieldRuleCount(datasetId, ownerId);
        List<String> allowedFields = dataPolicyMapper.allowedFields(datasetId, ownerId);
        List<String> rowRules = dataPolicyMapper.rowRules(datasetId, ownerId);
        return fieldRuleCount + "|" + String.join(",", allowedFields) + "|" + String.join("\n", rowRules);
    }

    /**
     * 将 {@code null} 转为空串，便于拼接缓存材料。
     *
     * @param value 原始字符串
     * @return 非 null 字符串
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 计算材料的 SHA-256 十六进制摘要。
     *
     * @param material 缓存键材料
     * @return 十六进制哈希
     */
    private static String sha256(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
