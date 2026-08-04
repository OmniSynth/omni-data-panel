package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.panel.entity.CollectionEntity;

/**
 * 集合持久化接口，映射 {@code bi_collection} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link CollectionService} 维护用户收藏的资源分组，
 * 并供 {@link com.omni.panel.service.SearchService} 检索。</p>
 */
public interface CollectionMapper extends BaseMapper<CollectionEntity> {
}
