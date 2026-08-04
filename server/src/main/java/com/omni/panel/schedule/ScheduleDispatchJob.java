package com.omni.panel.schedule;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import com.omni.panel.entity.ScheduleEntity;
import com.omni.panel.mapper.ScheduleMapper;
import com.omni.panel.service.DashboardRefreshService;
import com.omni.panel.service.MetadataService;
import com.omni.panel.service.SubscriptionDeliveryService;
import com.omni.panel.service.UserAuthenticationService;

/**
 * Quartz 通用调度入口，根据持久化任务类型分派元数据同步、仪表盘刷新或订阅发送。
 * 执行前加载任务所有者身份，保证依赖权限的下游逻辑按所有者上下文运行。
 */
@DisallowConcurrentExecution
public class ScheduleDispatchJob implements Job {
    @Autowired
    private ScheduleMapper mapper;
    @Autowired
    private MetadataService metadataService;
    @Autowired
    private DashboardRefreshService dashboardRefreshService;
    @Autowired
    private SubscriptionDeliveryService subscriptionDeliveryService;
    @Autowired
    private UserAuthenticationService authenticationService;

    /**
     * 执行仍存在且处于启用状态的调度任务，成功后回写最近执行时间。
     *
     * @param context Quartz 执行上下文，必须包含 {@code scheduleId}
     * @throws JobExecutionException 任务分派或执行失败
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long scheduleId = context.getMergedJobDataMap().getLong("scheduleId");
        ScheduleEntity schedule = mapper.selectById(scheduleId);
        if (schedule == null || !Boolean.TRUE.equals(schedule.getEnabled())) {
            return;
        }
        var originalContext = SecurityContextHolder.getContext();
        var ownerContext = SecurityContextHolder.createEmptyContext();
        try {
            ownerContext.setAuthentication(authenticationService.load(schedule.getOwnerId()));
            SecurityContextHolder.setContext(ownerContext);
            switch (schedule.getScheduleType()) {
                case "METADATA_SYNC" -> metadataService.syncSystem(schedule.getTargetId());
                case "DASHBOARD_REFRESH" -> dashboardRefreshService.refresh(schedule.getTargetId());
                case "SUBSCRIPTION" -> subscriptionDeliveryService.send(schedule.getTargetId());
                default -> throw new IllegalArgumentException("不支持的调度类型");
            }
            mapper.markRun(scheduleId);
        } catch (Exception exception) {
            throw new JobExecutionException("调度任务执行失败", exception, false);
        } finally {
            SecurityContextHolder.setContext(originalContext);
        }
    }
}
