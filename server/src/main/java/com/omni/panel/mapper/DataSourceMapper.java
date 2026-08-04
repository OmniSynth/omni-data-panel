package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.panel.entity.DataSourceEntity;

/**
 * 数据源持久化接口，映射 {@code bi_data_source} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link DataSourceService} 维护连接配置，
 * 并供查询、元数据同步与健康检查等模块读取。</p>
 */
public interface DataSourceMapper extends BaseMapper<DataSourceEntity> {
}
