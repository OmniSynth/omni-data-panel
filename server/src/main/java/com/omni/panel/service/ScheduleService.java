package com.omni.panel.service;

import java.util.List;
import java.util.Set;

import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.ScheduleEntity;
import com.omni.panel.entity.SubscriptionEntity;
import com.omni.panel.mapper.ResourceOwnerMapper;
import com.omni.panel.mapper.ScheduleMapper;
import com.omni.panel.mapper.SubscriptionMapper;
import com.omni.panel.schedule.ScheduleDispatchJob;

/**
 * 管理通用调度任务的持久化与 Quartz 生命周期，并按当前用户隔离任务所有权。
 */
@Service
public class ScheduleService {
    private static final Set<String> TYPES = Set.of("METADATA_SYNC", "DASHBOARD_REFRESH", "SUBSCRIPTION");
    private final ScheduleMapper mapper;
    private final Scheduler scheduler;
    private final DataSourceService dataSourceService;
    private final PermissionService permissionService;
    private final ResourceOwnerMapper ownerMapper;
    private final SubscriptionMapper subscriptionMapper;

    /**
     * 注入调度持久化、Quartz 调度器与目标资源校验依赖。
     *
     * @param mapper              调度任务数据访问
     * @param scheduler           Quartz 调度器
     * @param dataSourceService   数据源服务
     * @param permissionService   权限服务
     * @param ownerMapper         资源所有者查询
     * @param subscriptionMapper  订阅数据访问
     */
    public ScheduleService(ScheduleMapper mapper, Scheduler scheduler, DataSourceService dataSourceService,
                           PermissionService permissionService, ResourceOwnerMapper ownerMapper,
                           SubscriptionMapper subscriptionMapper) {
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.dataSourceService = dataSourceService;
        this.permissionService = permissionService;
        this.ownerMapper = ownerMapper;
        this.subscriptionMapper = subscriptionMapper;
    }

    /**
     * 查询当前用户拥有的任务；管理员可查看全部任务。
     *
     * @return 当前用户可管理的调度任务列表
     */
    public List<ScheduleEntity> list() {
        AuthenticatedUser user = AuthenticatedUser.current();
        return mapper.selectList(null).stream()
                .filter(item -> user.admin() || item.getOwnerId() == user.id()).toList();
    }

    /**
     * 创建或更新调度任务，并用最新配置替换对应 Quartz 作业。
     *
     * @param id       调度任务标识，为 {@code null} 时创建任务
     * @param name     任务名称
     * @param type     调度类型
     * @param targetId 调度目标标识
     * @param cron     Quartz Cron 表达式
     * @param payload  任务载荷 JSON
     * @param enabled  是否启用
     * @return 已持久化的调度任务
     */
    public ScheduleEntity save(Long id, String name, String type, long targetId,
                               String cron, String payload, boolean enabled) {
        String normalized = type.toUpperCase();
        if (!TYPES.contains(normalized) || !CronExpression.isValidExpression(cron)) {
            throw new BusinessException("调度类型或 Cron 表达式不合法");
        }
        validateTarget(normalized, targetId);
        ScheduleEntity entity = id == null ? new ScheduleEntity() : require(id);
        entity.setName(name);
        entity.setScheduleType(normalized);
        entity.setTargetId(targetId);
        entity.setCronExpression(cron);
        entity.setPayloadJson(payload);
        entity.setEnabled(enabled);
        if (id == null) {
            entity.setOwnerId(AuthenticatedUser.current().id());
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        register(entity);
        return entity;
    }

    /**
     * 删除当前用户可管理的任务及对应 Quartz 作业。
     *
     * @param id 调度任务标识
     */
    public void delete(long id) {
        require(id);
        mapper.deleteById(id);
        try {
            scheduler.deleteJob(jobKey(id));
        } catch (SchedulerException exception) {
            throw new BusinessException("删除 Quartz 任务失败");
        }
    }

    /**
     * 按调度类型校验目标资源存在且当前用户有权访问。
     *
     * @param type     调度类型
     * @param targetId 目标资源标识
     */
    private void validateTarget(String type, long targetId) {
        switch (type) {
            case "METADATA_SYNC" -> dataSourceService.require(targetId, "READ");
            case "DASHBOARD_REFRESH" -> {
                Long owner = ownerMapper.dashboardOwner(targetId);
                if (owner == null) {
                    throw new BusinessException(404, "仪表盘不存在");
                }
                permissionService.require("DASHBOARD", targetId, owner, "READ");
            }
            case "SUBSCRIPTION" -> {
                SubscriptionEntity subscription = subscriptionMapper.selectById(targetId);
                AuthenticatedUser user = AuthenticatedUser.current();
                if (subscription == null) {
                    throw new BusinessException(404, "订阅不存在");
                }
                if (!user.admin() && subscription.getOwnerId() != user.id()) {
                    throw new BusinessException(403, "无权访问该订阅");
                }
            }
            default -> throw new BusinessException("调度类型不合法");
        }
    }

    /**
     * 加载调度任务并校验当前用户管理权限。
     *
     * @param id 调度任务标识
     * @return 调度任务实体
     */
    private ScheduleEntity require(long id) {
        ScheduleEntity entity = mapper.selectById(id);
        AuthenticatedUser user = AuthenticatedUser.current();
        if (entity == null) {
            throw new BusinessException(404, "调度任务不存在");
        }
        if (!user.admin() && entity.getOwnerId() != user.id()) {
            throw new BusinessException(403, "无权管理该调度任务");
        }
        return entity;
    }

    /**
     * 将调度任务注册或更新到 Quartz；禁用时仅删除旧作业。
     *
     * @param entity 调度任务实体
     */
    private void register(ScheduleEntity entity) {
        try {
            scheduler.deleteJob(jobKey(entity.getId()));
            if (!Boolean.TRUE.equals(entity.getEnabled())) {
                return;
            }
            var job = JobBuilder.newJob(ScheduleDispatchJob.class).withIdentity(jobKey(entity.getId()))
                    .storeDurably()
                    .requestRecovery(true)
                    .usingJobData("scheduleId", entity.getId()).build();
            var trigger = TriggerBuilder.newTrigger().withIdentity("schedule-trigger-" + entity.getId())
                    .withSchedule(CronScheduleBuilder.cronSchedule(entity.getCronExpression())
                            .withMisfireHandlingInstructionDoNothing()).build();
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException exception) {
            throw new BusinessException("注册 Quartz 任务失败：" + exception.getMessage());
        }
    }

    /**
     * 应用就绪后从数据库恢复全部启用任务。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        mapper.selectList(null).stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).forEach(this::register);
    }

    /**
     * 构建调度任务在 Quartz 中的作业键。
     *
     * @param id 调度任务标识
     * @return Quartz 作业键
     */
    private JobKey jobKey(long id) {
        return JobKey.jobKey("schedule-" + id);
    }
}
