package com.omni.panel.setting;

import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 管理系统设置键值。
 */
@Service
public class SettingService {
    private static final Set<String> ALLOWED = Set.of("site.name", "embed.enabled");
    private final SettingMapper mapper;

    public SettingService(SettingMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 读取全部允许的设置。
     *
     * @return 设置映射
     */
    public Map<String, String> list() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : ALLOWED) {
            SettingEntity entity = mapper.selectById(key);
            if (entity != null) {
                values.put(key, entity.getSettingValue());
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
            SettingEntity entity = mapper.selectById(entry.getKey());
            boolean insert = entity == null;
            if (insert) {
                entity = new SettingEntity();
                entity.setSettingKey(entry.getKey());
            }
            entity.setSettingValue(entry.getValue() == null ? "" : entry.getValue());
            entity.setUpdatedAt(LocalDateTime.now());
            if (insert) {
                mapper.insert(entity);
            } else {
                mapper.updateById(entity);
            }
        }
        return list();
    }
}
