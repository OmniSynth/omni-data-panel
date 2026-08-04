package com.omni.panel.service;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.DashboardCardEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.entity.ScheduleEntity;
import com.omni.panel.entity.SubscriptionEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.DashboardCardMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.ScheduleMapper;
import com.omni.panel.mapper.SubscriptionMapper;

/**
 * 管理仪表盘的读写、集合归属与软删生命周期。
 */
@Service
public class DashboardService {
    private final DashboardMapper dashboardMapper;
    private final DashboardCardMapper cardMapper;
    private final ChartMapper chartMapper;
    private final PermissionService permissionService;
    private final CollectionService collectionService;
    private final SubscriptionMapper subscriptionMapper;
    private final ScheduleMapper scheduleMapper;

    public DashboardService(DashboardMapper dashboardMapper, DashboardCardMapper cardMapper,
                            ChartMapper chartMapper, PermissionService permissionService,
                            @Lazy CollectionService collectionService,
                            SubscriptionMapper subscriptionMapper, ScheduleMapper scheduleMapper) {
        this.dashboardMapper = dashboardMapper;
        this.cardMapper = cardMapper;
        this.chartMapper = chartMapper;
        this.permissionService = permissionService;
        this.collectionService = collectionService;
        this.subscriptionMapper = subscriptionMapper;
        this.scheduleMapper = scheduleMapper;
    }

    /**
     * 查询可读且未删除的仪表盘。
     *
     * @return 仪表盘列表
     */
    public List<DashboardEntity> listReadable() {
        return dashboardMapper.selectList(Wrappers.<DashboardEntity>lambdaQuery()
                        .isNull(DashboardEntity::getDeletedAt))
                .stream()
                .filter(item -> permissionService.canRead("DASHBOARD", item.getId(), item.getOwnerId()))
                .toList();
    }

    /**
     * 查询废纸篓中的仪表盘。
     *
     * @return 已软删仪表盘
     */
    public List<DashboardEntity> listTrash() {
        AuthenticatedUser user = AuthenticatedUser.current();
        return dashboardMapper.selectList(Wrappers.<DashboardEntity>lambdaQuery()
                        .isNotNull(DashboardEntity::getDeletedAt).orderByDesc(DashboardEntity::getDeletedAt))
                .stream()
                .filter(item -> user.admin() || item.getOwnerId().equals(user.id()))
                .toList();
    }

    /**
     * 获取仪表盘并校验权限。
     *
     * @param id         仪表盘标识
     * @param permission 权限
     * @return 仪表盘
     */
    public DashboardEntity require(long id, String permission) {
        DashboardEntity dashboard = dashboardMapper.selectById(id);
        if (dashboard == null || dashboard.getDeletedAt() != null) {
            throw new BusinessException(404, "仪表盘不存在");
        }
        permissionService.require("DASHBOARD", id, dashboard.getOwnerId(), permission);
        return dashboard;
    }

    /**
     * 创建仪表盘。
     *
     * @param name         名称
     * @param description  描述
     * @param configJson   配置
     * @param collectionId 集合标识
     * @return 已创建仪表盘
     */
    @Transactional
    public DashboardEntity create(String name, String description, String configJson, Long collectionId) {
        AuthenticatedUser user = AuthenticatedUser.current();
        DashboardEntity dashboard = new DashboardEntity();
        dashboard.setName(name);
        dashboard.setDescription(description);
        dashboard.setConfigJson(configJson);
        dashboard.setOwnerId(user.id());
        dashboard.setCollectionId(resolveCollectionId(collectionId, user.id()));
        dashboard.setUpdatedAt(LocalDateTime.now());
        dashboardMapper.insert(dashboard);
        return dashboard;
    }

    /**
     * 更新仪表盘。
     *
     * @param id           仪表盘标识
     * @param name         名称
     * @param description  描述
     * @param configJson   配置
     * @param collectionId 集合标识
     * @return 已更新仪表盘
     */
    @Transactional
    public DashboardEntity update(long id, String name, String description, String configJson, Long collectionId) {
        DashboardEntity dashboard = require(id, "WRITE");
        dashboard.setName(name);
        dashboard.setDescription(description);
        dashboard.setConfigJson(configJson);
        if (collectionId != null) {
            collectionService.requireReadable(collectionId);
            dashboard.setCollectionId(collectionId);
        }
        dashboard.setUpdatedAt(LocalDateTime.now());
        dashboardMapper.updateById(dashboard);
        return dashboard;
    }

    /**
     * 软删除仪表盘。
     *
     * @param id 仪表盘标识
     */
    @Transactional
    public void softDelete(long id) {
        DashboardEntity dashboard = require(id, "WRITE");
        dashboard.setDeletedAt(LocalDateTime.now());
        dashboard.setUpdatedAt(dashboard.getDeletedAt());
        dashboardMapper.updateById(dashboard);
    }

    /**
     * 恢复仪表盘。
     *
     * @param id 仪表盘标识
     */
    @Transactional
    public void restore(long id) {
        DashboardEntity dashboard = requireTrashed(id);
        dashboard.setDeletedAt(null);
        dashboard.setUpdatedAt(LocalDateTime.now());
        dashboardMapper.updateById(dashboard);
    }

    /**
     * 永久删除仪表盘；存在订阅或调度引用时拒绝。
     *
     * @param id 仪表盘标识
     */
    @Transactional
    public void purge(long id) {
        DashboardEntity dashboard = requireTrashed(id);
        long subscriptions = subscriptionMapper.selectCount(Wrappers.<SubscriptionEntity>lambdaQuery()
                .eq(SubscriptionEntity::getDashboardId, id));
        if (subscriptions > 0) {
            throw new BusinessException("仪表盘仍被订阅引用，无法永久删除");
        }
        long schedules = scheduleMapper.selectCount(Wrappers.<ScheduleEntity>lambdaQuery()
                .eq(ScheduleEntity::getScheduleType, "DASHBOARD_REFRESH")
                .eq(ScheduleEntity::getTargetId, id));
        if (schedules > 0) {
            throw new BusinessException("仪表盘仍被调度任务引用，无法永久删除");
        }
        permissionService.deleteResource("DASHBOARD", id);
        dashboardMapper.deleteById(dashboard.getId());
    }

    /**
     * 校验卡片关联图表可读。
     *
     * @param chartId 图表标识
     */
    public void requireChart(long chartId) {
        ChartEntity chart = chartMapper.selectById(chartId);
        if (chart == null || chart.getDeletedAt() != null) {
            throw new BusinessException(404, "图表不存在");
        }
        permissionService.require("CHART", chartId, chart.getOwnerId(), "READ");
    }

    /**
     * 查询仪表盘卡片。
     *
     * @param dashboardId 仪表盘标识
     * @return 卡片列表
     */
    public List<DashboardCardEntity> cards(long dashboardId) {
        require(dashboardId, "READ");
        return cardMapper.selectList(Wrappers.<DashboardCardEntity>lambdaQuery()
                .eq(DashboardCardEntity::getDashboardId, dashboardId).orderByAsc(DashboardCardEntity::getId));
    }

    /**
     * 创建卡片。
     *
     * @param dashboardId     仪表盘标识
     * @param chartId         图表标识
     * @param title           标题
     * @param layoutJson      布局
     * @param bindingsJson    参数绑定
     * @param clickActionJson 点击联动
     * @return 已创建卡片
     */
    @Transactional
    public DashboardCardEntity createCard(long dashboardId, long chartId, String title, String layoutJson,
                                          String bindingsJson, String clickActionJson) {
        require(dashboardId, "WRITE");
        requireChart(chartId);
        DashboardCardEntity card = new DashboardCardEntity();
        card.setDashboardId(dashboardId);
        card.setChartId(chartId);
        card.setTitle(title);
        card.setLayoutJson(layoutJson);
        card.setBindingsJson(bindingsJson == null || bindingsJson.isBlank() ? "[]" : bindingsJson);
        card.setClickActionJson(blankToNull(clickActionJson));
        cardMapper.insert(card);
        return card;
    }

    /**
     * 更新卡片。
     *
     * @param dashboardId     仪表盘标识
     * @param cardId          卡片标识
     * @param chartId         图表标识
     * @param title           标题
     * @param layoutJson      布局
     * @param bindingsJson    参数绑定
     * @param clickActionJson 点击联动
     * @return 已更新卡片
     */
    @Transactional
    public DashboardCardEntity updateCard(long dashboardId, long cardId, long chartId,
                                          String title, String layoutJson,
                                          String bindingsJson, String clickActionJson) {
        require(dashboardId, "WRITE");
        requireChart(chartId);
        DashboardCardEntity card = requireCard(dashboardId, cardId);
        card.setChartId(chartId);
        card.setTitle(title);
        card.setLayoutJson(layoutJson);
        if (bindingsJson != null) {
            card.setBindingsJson(bindingsJson.isBlank() ? "[]" : bindingsJson);
        }
        if (clickActionJson != null) {
            card.setClickActionJson(blankToNull(clickActionJson));
        }
        cardMapper.updateById(card);
        return card;
    }

    /**
     * 将空白字符串规范为 {@code null}。
     *
     * @param value 原始字符串
     * @return 非空白字符串或 {@code null}
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 删除卡片。
     *
     * @param dashboardId 仪表盘标识
     * @param cardId      卡片标识
     */
    @Transactional
    public void deleteCard(long dashboardId, long cardId) {
        require(dashboardId, "WRITE");
        cardMapper.deleteById(requireCard(dashboardId, cardId));
    }

    /**
     * 加载属于指定仪表盘的卡片，不存在或不匹配时抛出 404。
     *
     * @param dashboardId 仪表盘标识
     * @param cardId      卡片标识
     * @return 卡片实体
     */
    private DashboardCardEntity requireCard(long dashboardId, long cardId) {
        DashboardCardEntity card = cardMapper.selectById(cardId);
        if (card == null || card.getDashboardId() == null || card.getDashboardId() != dashboardId) {
            throw new BusinessException(404, "卡片不存在");
        }
        return card;
    }

    /**
     * 加载废纸篓中的仪表盘并校验当前用户操作权限。
     *
     * @param id 仪表盘标识
     * @return 已软删的仪表盘
     */
    private DashboardEntity requireTrashed(long id) {
        DashboardEntity dashboard = dashboardMapper.selectById(id);
        AuthenticatedUser user = AuthenticatedUser.current();
        if (dashboard == null || dashboard.getDeletedAt() == null) {
            throw new BusinessException(404, "废纸篓中不存在该仪表盘");
        }
        if (!user.admin() && !dashboard.getOwnerId().equals(user.id())) {
            throw new BusinessException(403, "无权操作该仪表盘");
        }
        return dashboard;
    }

    /**
     * 解析目标集合标识；未指定时使用用户个人根集合。
     *
     * @param collectionId 可选集合标识
     * @param userId       用户标识
     * @return 有效集合标识
     */
    private long resolveCollectionId(Long collectionId, long userId) {
        if (collectionId != null) {
            collectionService.requireReadable(collectionId);
            return collectionId;
        }
        return collectionService.ensurePersonalCollection(userId).getId();
    }
}
