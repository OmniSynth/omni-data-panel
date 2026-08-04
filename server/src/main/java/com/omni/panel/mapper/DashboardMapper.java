package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;
import com.omni.panel.entity.DashboardEntity;

/**
 * 仪表盘持久化接口，映射 {@code bi_dashboard} 表。
 *
 * <p>继承 MyBatis-Plus {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} 提供仪表盘 CRUD，
 * 并支持将最近刷新时间回写至数据库。</p>
 */
public interface DashboardMapper extends BaseMapper<DashboardEntity> {
    /**
     * 将指定仪表盘的最近刷新时间更新为数据库当前时间。
     *
     * @param dashboardId 仪表盘标识
     * @return 实际更新的记录数
     */
    @Update("UPDATE bi_dashboard SET last_refreshed_at = CURRENT_TIMESTAMP WHERE id = #{dashboardId}")
    int markRefreshed(long dashboardId);
}
