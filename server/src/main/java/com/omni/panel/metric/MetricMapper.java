package com.omni.panel.metric;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 指标持久化接口，映射 {@code bi_metric} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link MetricService} 维护指标定义，
 * 并供 {@link com.omni.panel.collection.CollectionService}、
 * {@link com.omni.panel.search.SearchService} 聚合检索。</p>
 */
public interface MetricMapper extends BaseMapper<MetricEntity> {
}
