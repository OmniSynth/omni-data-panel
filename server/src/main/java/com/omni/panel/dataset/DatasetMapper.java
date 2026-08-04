package com.omni.panel.dataset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 数据集持久化接口，映射 {@code bi_dataset} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link DatasetService} 维护数据集定义，
 * 并供图表、指标、集合与检索等模块读取。</p>
 */
public interface DatasetMapper extends BaseMapper<DatasetEntity> {
}
