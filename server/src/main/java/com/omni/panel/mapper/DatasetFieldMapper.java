package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.panel.entity.DatasetFieldEntity;

/**
 * 数据集字段持久化接口，映射 {@code bi_dataset_field} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link DatasetService} 维护数据集的语义字段定义。</p>
 */
public interface DatasetFieldMapper extends BaseMapper<DatasetFieldEntity> {
}
