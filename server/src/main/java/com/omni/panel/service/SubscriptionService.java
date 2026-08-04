package com.omni.panel.service;

import java.util.List;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.SubscriptionEntity;
import com.omni.panel.mapper.ResourceOwnerMapper;
import com.omni.panel.mapper.SubscriptionMapper;
import com.omni.panel.subscription.SubscriptionDispatchJob;

/**
 * 管理仪表盘邮件订阅的权限、持久化与 Quartz 生命周期。
 */
@Service
public class SubscriptionService {
    private final SubscriptionMapper mapper;
    private final PermissionService permissionService;
    private final ResourceOwnerMapper ownerMapper;
    private final SubscriptionDeliveryService deliveryService;
    private final Scheduler scheduler;

    public SubscriptionService(SubscriptionMapper mapper, PermissionService permissionService,
                               ResourceOwnerMapper ownerMapper, SubscriptionDeliveryService deliveryService,
                               Scheduler scheduler) {
        this.mapper = mapper;
        this.permissionService = permissionService;
        this.ownerMapper = ownerMapper;
        this.deliveryService = deliveryService;
        this.scheduler = scheduler;
    }

    /**
     * 查询当前用户拥有的订阅；管理员可查看全部订阅。
     *
     * @return 当前用户可管理的订阅列表
     */
    public List<SubscriptionEntity> list() {
        AuthenticatedUser user = AuthenticatedUser.current();
        return mapper.selectList(null).stream()
                .filter(item -> user.admin() || item.getOwnerId() == user.id()).toList();
    }

    /**
     * 创建或更新订阅，并用最新配置替换对应 Quartz 作业。
     * 保存前要求当前用户可读目标仪表盘，并校验 Cron 与收件人；禁用订阅会保留数据库记录但移除 Quartz 作业。
     *
     * @param id          订阅标识，为 {@code null} 时创建订阅
     * @param name        订阅名称
     * @param dashboardId 仪表盘标识
     * @param cron        Quartz Cron 表达式
     * @param recipients  收件人地址文本
     * @param enabled     是否启用
     * @return 已持久化的订阅
     */
    public SubscriptionEntity save(Long id, String name, long dashboardId, String cron,
                                   String recipients, boolean enabled) {
        permissionService.require("DASHBOARD", dashboardId, dashboardOwner(dashboardId), "READ");
        if (!CronExpression.isValidExpression(cron)) {
            throw new BusinessException("Cron 表达式不合法");
        }
        deliveryService.parseRecipients(recipients);
        SubscriptionEntity entity = id == null ? new SubscriptionEntity() : require(id);
        entity.setName(name);
        entity.setDashboardId(dashboardId);
        entity.setCronExpression(cron);
        entity.setRecipients(recipients);
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
     * 删除当前用户可管理的订阅及对应 Quartz 作业。
     * 数据库记录删除后若 Quartz 删除失败，将抛出业务异常。
     *
     * @param id 订阅标识
     */
    public void delete(long id) {
        require(id);
        mapper.deleteById(id);
        deleteJob(id);
    }

    /**
     * 加载订阅并校验当前用户管理权限。
     *
     * @param id 订阅标识
     * @return 订阅实体
     */
    private SubscriptionEntity require(long id) {
        SubscriptionEntity entity = mapper.selectById(id);
        AuthenticatedUser user = AuthenticatedUser.current();
        if (entity == null) {
            throw new BusinessException(404, "订阅不存在");
        }
        if (!user.admin() && entity.getOwnerId() != user.id()) {
            throw new BusinessException(403, "无权访问该订阅");
        }
        return entity;
    }

    /**
     * 查询仪表盘所有者标识，不存在时抛出 404。
     *
     * @param dashboardId 仪表盘标识
     * @return 所有者用户标识
     */
    private long dashboardOwner(long dashboardId) {
        Long owner = ownerMapper.dashboardOwner(dashboardId);
        if (owner == null) {
            throw new BusinessException(404, "仪表盘不存在");
        }
        return owner;
    }

    /**
     * 先清理同标识旧作业，再按启用状态决定是否以最新 Cron 配置重新注册。
     *
     * @param entity 已持久化的订阅
     */
    private void register(SubscriptionEntity entity) {
        deleteJob(entity.getId());
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            return;
        }
        try {
            var job = JobBuilder.newJob(SubscriptionDispatchJob.class).withIdentity(jobKey(entity.getId()))
                    .usingJobData("subscriptionId", entity.getId()).build();
            var trigger = TriggerBuilder.newTrigger().withIdentity("subscription-trigger-" + entity.getId())
                    .withSchedule(CronScheduleBuilder.cronSchedule(entity.getCronExpression())
                            .withMisfireHandlingInstructionDoNothing()).build();
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException exception) {
            throw new BusinessException("注册订阅任务失败：" + exception.getMessage());
        }
    }

    /**
     * 从 Quartz 删除订阅作业，使删除、禁用和更新均不会遗留旧触发器。
     *
     * @param id 订阅标识
     */
    private void deleteJob(long id) {
        try {
            scheduler.deleteJob(jobKey(id));
        } catch (SchedulerException exception) {
            throw new BusinessException("变更订阅任务失败：" + exception.getMessage());
        }
    }

    /**
     * 应用就绪后从数据库恢复全部启用订阅；每个订阅均清理并重建对应 Quartz 作业。
     * 任一 Quartz 变更失败会以业务异常终止后续恢复。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        mapper.selectList(null).stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).forEach(this::register);
    }

    /**
     * 构建订阅任务在 Quartz 中的作业键。
     *
     * @param id 订阅标识
     * @return Quartz 作业键
     */
    private JobKey jobKey(long id) {
        return JobKey.jobKey("subscription-" + id);
    }
}
