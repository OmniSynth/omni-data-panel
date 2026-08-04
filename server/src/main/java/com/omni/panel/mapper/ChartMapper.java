package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.panel.entity.ChartEntity;

/**
 * 图表持久化接口，映射 {@code bi_chart} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link ChartService} 维护图表定义，
 * 并供仪表板渲染、最近访问与集合等模块读取。</p>
 */
public interface ChartMapper extends BaseMapper<ChartEntity> {
}
