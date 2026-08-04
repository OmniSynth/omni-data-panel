package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.panel.entity.SettingEntity;

/**
 * 系统设置持久化接口，映射 {@code bi_setting} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link SettingService} 读写键值配置。</p>
 */
public interface SettingMapper extends BaseMapper<SettingEntity> {
}
