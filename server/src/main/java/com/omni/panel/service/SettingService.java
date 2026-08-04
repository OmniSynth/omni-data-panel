package com.omni.panel.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.SettingEntity;
import com.omni.panel.mapper.SettingMapper;

/**
 * 管理系统设置键值。
 */
@Service
public class SettingService {
    static final String CACHE_QUERY_ENABLED = "cache.query.enabled";
    static final String CACHE_QUERY_TTL_SECONDS = "cache.query.ttl-seconds";
    private static final int DEFAULT_CACHE_TTL_SECONDS = 300;
    private static final int MIN_CACHE_TTL_SECONDS = 30;
    private static final int MAX_CACHE_TTL_SECONDS = 86_400;
    private static final List<String> ALLOWED_KEYS = List.of(
            "site.name",
            "embed.enabled",
            CACHE_QUERY_ENABLED,
            CACHE_QUERY_TTL_SECONDS);
    private static final Set<String> ALLOWED = new LinkedHashSet<>(ALLOWED_KEYS);
    private static final Map<String, String> DEFAULTS = Map.of(
            "site.name", "全域数据分析",
            "embed.enabled", "true",
            CACHE_QUERY_ENABLED, "false",
            CACHE_QUERY_TTL_SECONDS, String.valueOf(DEFAULT_CACHE_TTL_SECONDS));

    private final SettingMapper mapper;

    public SettingService(SettingMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 读取全部允许的设置；库中缺失时回落默认值。
     *
     * @return 设置映射
     */
    public Map<String, String> list() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : ALLOWED_KEYS) {
            SettingEntity entity = mapper.selectById(key);
            if (entity != null) {
                values.put(key, entity.getSettingValue());
            } else {
                values.put(key, DEFAULTS.get(key));
            }
        }
        return values;
    }

    /**
     * 读取单个设置值。
     *
     * @param key 设置键
     * @return 设置值；不存在时返回 {@code null}
     */
    public String get(String key) {
        SettingEntity entity = mapper.selectById(key);
        return entity == null ? null : entity.getSettingValue();
    }

    /**
     * 判断嵌入功能是否启用。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean embedEnabled() {
        String value = get("embed.enabled");
        return value == null || Boolean.parseBoolean(value);
    }

    /**
     * 判断查询结果缓存是否启用。
     *
     * @return 启用时返回 {@code true}；缺省关闭
     */
    public boolean queryCacheEnabled() {
        String value = get(CACHE_QUERY_ENABLED);
        return value != null && Boolean.parseBoolean(value);
    }

    /**
     * 读取查询结果缓存 TTL（秒）。
     *
     * @return 有效秒数；缺省或非法时回落 300
     */
    public int queryCacheTtlSeconds() {
        String value = get(CACHE_QUERY_TTL_SECONDS);
        if (value == null || value.isBlank()) {
            return DEFAULT_CACHE_TTL_SECONDS;
        }
        try {
            int seconds = Integer.parseInt(value.trim());
            if (seconds < MIN_CACHE_TTL_SECONDS || seconds > MAX_CACHE_TTL_SECONDS) {
                return DEFAULT_CACHE_TTL_SECONDS;
            }
            return seconds;
        } catch (NumberFormatException exception) {
            return DEFAULT_CACHE_TTL_SECONDS;
        }
    }

    /**
     * 批量更新设置，仅管理员。
     *
     * @param values 设置映射
     * @return 更新后的设置
     */
    @Transactional
    public Map<String, String> update(Map<String, String> values) {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可修改系统设置");
        }
        if (values == null || values.isEmpty()) {
            throw new BusinessException("设置不能为空");
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!ALLOWED.contains(entry.getKey())) {
                throw new BusinessException("不支持的设置键：" + entry.getKey());
            }
            String normalized = normalizeValue(entry.getKey(), entry.getValue());
            SettingEntity entity = mapper.selectById(entry.getKey());
            boolean insert = entity == null;
            if (insert) {
                entity = new SettingEntity();
                entity.setSettingKey(entry.getKey());
            }
            entity.setSettingValue(normalized);
            entity.setUpdatedAt(LocalDateTime.now());
            if (insert) {
                mapper.insert(entity);
            } else {
                mapper.updateById(entity);
            }
        }
        return list();
    }

    /**
     * 校验并规范化单个设置值（布尔开关、TTL 范围等）。
     *
     * @param key   设置键
     * @param value 原始值
     * @return 规范化后的存储值
     */
    private String normalizeValue(String key, String value) {
        String raw = value == null ? "" : value.trim();
        if (CACHE_QUERY_ENABLED.equals(key)) {
            if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
                throw new BusinessException("缓存开关仅支持 true 或 false");
            }
            return Boolean.parseBoolean(raw) ? "true" : "false";
        }
        if (CACHE_QUERY_TTL_SECONDS.equals(key)) {
            int seconds;
            try {
                seconds = Integer.parseInt(raw);
            } catch (NumberFormatException exception) {
                throw new BusinessException("缓存时间必须是整数秒");
            }
            if (seconds < MIN_CACHE_TTL_SECONDS || seconds > MAX_CACHE_TTL_SECONDS) {
                throw new BusinessException("缓存时间需在 " + MIN_CACHE_TTL_SECONDS
                        + "–" + MAX_CACHE_TTL_SECONDS + " 秒之间");
            }
            return String.valueOf(seconds);
        }
        return raw;
    }
}
