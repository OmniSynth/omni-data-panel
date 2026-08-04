package com.omni.panel.collection;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 集合持久化接口，映射 {@code bi_collection} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link CollectionService} 维护用户收藏的资源分组，
 * 并供 {@link com.omni.panel.search.SearchService} 检索。</p>
 */
public interface CollectionMapper extends BaseMapper<CollectionEntity> {
}
