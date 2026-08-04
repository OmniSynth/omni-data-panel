package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.panel.entity.SubscriptionEntity;

/**
 * 仪表盘邮件订阅持久化接口，映射 {@code bi_subscription} 表。
 *
 * <p>继承 MyBatis-Plus {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} 提供订阅记录 CRUD，
 * 供订阅创建、投递状态跟踪及生命周期管理使用。</p>
 */
public interface SubscriptionMapper extends BaseMapper<SubscriptionEntity> {
}
