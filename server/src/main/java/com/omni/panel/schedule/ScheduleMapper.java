package com.omni.panel.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;

/**
 * 通用调度任务持久化接口，提供任务读写及执行时间回写能力。
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
