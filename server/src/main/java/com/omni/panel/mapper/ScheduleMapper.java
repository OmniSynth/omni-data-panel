package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;
import com.omni.panel.entity.ScheduleEntity;

/**
 * 通用调度任务持久化接口，映射 {@code bi_schedule} 表。
 *
 * <p>继承 MyBatis-Plus {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} 提供调度任务 CRUD，
 * 并支持将最近执行时间回写至数据库。</p>
 */
public interface ScheduleMapper extends BaseMapper<ScheduleEntity> {
    /**
     * 将指定任务的最近执行时间更新为数据库当前时间。
     *
     * @param scheduleId 调度任务标识
     * @return 实际更新的记录数
     */
    @Update("UPDATE bi_schedule SET last_run_at = CURRENT_TIMESTAMP WHERE id = #{scheduleId}")
    int markRun(long scheduleId);
}
