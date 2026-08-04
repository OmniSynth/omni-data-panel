package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.panel.entity.PublicLinkEntity;

/**
 * 公开分享链接持久化接口，映射 {@code bi_public_link} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link PublicLinkService} 管理仪表板与图表的外链访问配置。</p>
 */
public interface PublicLinkMapper extends BaseMapper<PublicLinkEntity> {
}
