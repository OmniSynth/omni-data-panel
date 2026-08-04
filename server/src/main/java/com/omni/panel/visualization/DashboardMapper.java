package com.omni.panel.visualization;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;

/**
 * 仪表盘持久化接口，提供基础读写及刷新时间回写能力。
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
