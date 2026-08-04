package com.omni.panel.visualization;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.omni.panel.auth.UserAuthenticationService;
import com.omni.panel.common.BusinessException;
import com.omni.panel.permission.PermissionService;
import com.omni.panel.query.JdbcQueryExecutor;
import com.omni.panel.query.QueryProperties;
import com.omni.panel.query.QueryService;
import com.omni.panel.query.QueryStateStore;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 以每张图表所有者的实时权限执行已保存查询，并生成不含查询定义的仪表盘渲染结果。
 */
@Service
public class DashboardRenderService {
    private final DashboardMapper dashboardMapper;
    private final DashboardCardMapper cardMapper;
    private final ChartMapper chartMapper;
    private final PermissionService permissionService;
    private final UserAuthenticationService authenticationService;
    private final QueryService queryService;
    private final QueryProperties queryProperties;
    private final ObjectMapper objectMapper;

    public DashboardRenderService(DashboardMapper dashboardMapper, DashboardCardMapper cardMapper,
                                  ChartMapper chartMapper, PermissionService permissionService,
                                  UserAuthenticationService authenticationService, QueryService queryService,
                                  QueryProperties queryProperties, ObjectMapper objectMapper) {
        this.dashboardMapper = dashboardMapper;
        this.cardMapper = cardMapper;
        this.chartMapper = chartMapper;
        this.permissionService = permissionService;
        this.authenticationService = authenticationService;
        this.queryService = queryService;
        this.queryProperties = queryProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验仪表盘读取权限并执行全部卡片查询。
     *
     * @param dashboardId 仪表盘标识
     * @return 不暴露查询定义和数据引用的卡片渲染结果
     */
    public RenderedDashboard render(long dashboardId) {
        DashboardEntity dashboard = requireDashboard(dashboardId);
        permissionService.require("DASHBOARD", dashboardId, dashboard.getOwnerId(), "READ");
        return new RenderedDashboard(dashboard.getId(), dashboard.getName(), dashboard.getConfigJson(),
            permissionService.accessLevel("DASHBOARD", dashboardId), executeCards(dashboardId));
    }

    /**
     * 以仪表盘所有者身份渲染，供公开/嵌入匿名访问使用。
     *
     * @param dashboardId 仪表盘标识
     * @return 脱敏渲染结果
     */
    public RenderedDashboard renderAsOwner(long dashboardId) {
        DashboardEntity dashboard = requireDashboard(dashboardId);
        var originalContext = SecurityContextHolder.getContext();
        var ownerContext = SecurityContextHolder.createEmptyContext();
        try {
            ownerContext.setAuthentication(authenticationService.load(dashboard.getOwnerId()));
            SecurityContextHolder.setContext(ownerContext);
            return new RenderedDashboard(dashboard.getId(), dashboard.getName(), dashboard.getConfigJson(),
                "READ", executeCards(dashboardId));
        } finally {
            SecurityContextHolder.setContext(originalContext);
        }
    }

    /**
     * 以问题所有者身份执行查询并返回脱敏结果。
     *
     * @param chartId 问题标识
     * @return 问题名称与查询结果
     */
    public RenderedQuestion renderQuestionAsOwner(long chartId) {
        ChartEntity chart = chartMapper.selectById(chartId);
        if (chart == null || chart.getDeletedAt() != null) {
            throw new BusinessException(404, "问题不存在");
        }
        Instant deadline = Instant.now().plus(Duration.ofSeconds(queryProperties.timeoutSeconds() + 5L));
        var originalContext = SecurityContextHolder.getContext();
        var ownerContext = SecurityContextHolder.createEmptyContext();
        try {
            ownerContext.setAuthentication(authenticationService.load(chart.getOwnerId()));
            SecurityContextHolder.setContext(ownerContext);
            QueryService.QuerySubmission submission =
                objectMapper.readValue(chart.getQueryJson(), QueryService.QuerySubmission.class);
            String queryId = queryService.submit(submission);
            QueryStateStore.QuerySnapshot snapshot = await(queryId, deadline);
            return new RenderedQuestion(chart.getId(), chart.getName(), chart.getChartType(),
                chart.getConfigJson(), snapshot.result(), null);
        } catch (BusinessException exception) {
            return new RenderedQuestion(chart.getId(), chart.getName(), chart.getChartType(),
                chart.getConfigJson(), null, "查询执行失败");
        } catch (Exception exception) {
            return new RenderedQuestion(chart.getId(), chart.getName(), chart.getChartType(),
                chart.getConfigJson(), null, "查询配置错误");
        } finally {
            SecurityContextHolder.setContext(originalContext);
        }
    }

    /**
     * 供已完成自身授权判断的后台刷新流程执行全部卡片查询。
     *
     * @param dashboardId 仪表盘标识
     * @return 卡片渲染结果
     */
    public List<RenderedCard> executeCards(long dashboardId) {
        requireDashboard(dashboardId);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(queryProperties.timeoutSeconds() + 5L));
        List<RenderedCard> results = new ArrayList<>();
        for (DashboardCardEntity card : cardMapper.selectList(Wrappers.<DashboardCardEntity>lambdaQuery()
            .eq(DashboardCardEntity::getDashboardId, dashboardId).orderByAsc(DashboardCardEntity::getId))) {
            results.add(executeCard(card, deadline));
        }
        return List.copyOf(results);
    }

    private RenderedCard executeCard(DashboardCardEntity card, Instant deadline) {
        ChartEntity chart = chartMapper.selectById(card.getChartId());
        if (chart == null || chart.getDeletedAt() != null) {
            return new RenderedCard(card.getId(), card.getTitle(), null,
                null, card.getLayoutJson(), null, "图表查询失败");
        }
        var originalContext = SecurityContextHolder.getContext();
        var ownerContext = SecurityContextHolder.createEmptyContext();
        try {
            ownerContext.setAuthentication(authenticationService.load(chart.getOwnerId()));
            SecurityContextHolder.setContext(ownerContext);
            QueryService.QuerySubmission submission =
                objectMapper.readValue(chart.getQueryJson(), QueryService.QuerySubmission.class);
            String queryId = queryService.submit(submission);
            QueryStateStore.QuerySnapshot snapshot = await(queryId, deadline);
            return new RenderedCard(card.getId(), card.getTitle(), chart.getChartType(),
                chart.getConfigJson(), card.getLayoutJson(), snapshot.result(), null);
        } catch (BusinessException exception) {
            return new RenderedCard(card.getId(), card.getTitle(), chart.getChartType(),
                chart.getConfigJson(), card.getLayoutJson(), null, "图表查询失败");
        } catch (Exception exception) {
            return new RenderedCard(card.getId(), card.getTitle(), chart.getChartType(),
                chart.getConfigJson(), card.getLayoutJson(), null, "图表查询配置错误");
        } finally {
            SecurityContextHolder.setContext(originalContext);
        }
    }

    /**
     * 不含查询定义和数据源引用的仪表盘渲染结果。
     */
    public record RenderedDashboard(@JsonSerialize(using = ToStringSerializer.class) long id,
                                    String name, String configJson, String accessLevel,
                                    List<RenderedCard> cards) {}

    /**
     * 公开/嵌入问题渲染结果。
     */
    public record RenderedQuestion(@JsonSerialize(using = ToStringSerializer.class) long id,
                                   String name, String chartType, String configJson,
                                   JdbcQueryExecutor.QueryResult result, String error) {}

    private QueryStateStore.QuerySnapshot await(String queryId, Instant deadline) {
        try {
            while (Instant.now().isBefore(deadline)) {
                QueryStateStore.QuerySnapshot snapshot = queryService.get(queryId);
                if ("SUCCEEDED".equals(snapshot.status())) {
                    return snapshot;
                }
                if ("FAILED".equals(snapshot.status()) || "CANCELLED".equals(snapshot.status())) {
                    throw new BusinessException("图表查询失败");
                }
                Thread.sleep(200);
            }
            queryService.cancel(queryId);
            throw new BusinessException("仪表盘渲染超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            try {
                queryService.cancel(queryId);
            } catch (Exception ignored) {
                // 尽力取消
            }
            throw new BusinessException("仪表盘渲染被中断");
        }
    }

    private DashboardEntity requireDashboard(long id) {
        DashboardEntity dashboard = dashboardMapper.selectById(id);
        if (dashboard == null || dashboard.getDeletedAt() != null) {
            throw new BusinessException(404, "仪表盘不存在");
        }
        return dashboard;
    }

    /**
     * 单张卡片的安全渲染结果。
     */
    public record RenderedCard(@JsonSerialize(using = ToStringSerializer.class) long cardId,
                               String title, String chartType, String configJson, String layoutJson,
                               JdbcQueryExecutor.QueryResult result, String error) {}
}
