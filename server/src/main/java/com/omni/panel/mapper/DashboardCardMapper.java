package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.panel.entity.DashboardCardEntity;

/**
 * 仪表盘卡片持久化接口，映射 {@code bi_dashboard_card} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link DashboardService} 与
 * {@link DashboardRenderService} 维护仪表板布局与卡片引用关系。</p>
 */
public interface DashboardCardMapper extends BaseMapper<DashboardCardEntity> {
}
