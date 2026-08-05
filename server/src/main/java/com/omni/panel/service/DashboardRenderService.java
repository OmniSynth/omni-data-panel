package com.omni.panel.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.DashboardCardEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.DashboardCardMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.query.JdbcQueryExecutor;
import com.omni.panel.query.QueryParameterApplier;
import com.omni.panel.query.QueryProperties;
import com.omni.panel.query.QueryStateStore;

/**
 * 以每张图表所有者的实时权限执行已保存查询，并生成不含查询定义的仪表盘渲染结果。
 *
 * <p>持有仪表盘 {@code READ} 的用户即可查看卡片结果：卡片查询在图表所有者安全上下文中执行，
 * 不要求查看者对图表本身具备列表级 {@code READ}（图表详情/列表仍走图表自身有效权限）。</p>
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
    private final ChartResultCache chartResultCache;
    private final QueryParameterApplier parameterApplier;

    public DashboardRenderService(DashboardMapper dashboardMapper, DashboardCardMapper cardMapper,
                                  ChartMapper chartMapper, PermissionService permissionService,
                                  UserAuthenticationService authenticationService, QueryService queryService,
                                  QueryProperties queryProperties, ObjectMapper objectMapper,
                                  ChartResultCache chartResultCache, QueryParameterApplier parameterApplier) {
        this.dashboardMapper = dashboardMapper;
        this.cardMapper = cardMapper;
        this.chartMapper = chartMapper;
        this.permissionService = permissionService;
        this.authenticationService = authenticationService;
        this.queryService = queryService;
        this.queryProperties = queryProperties;
        this.objectMapper = objectMapper;
        this.chartResultCache = chartResultCache;
        this.parameterApplier = parameterApplier;
    }

    /**
     * 校验仪表盘读取权限并执行全部卡片查询。
     *
     * @param dashboardId     仪表盘标识
     * @param forceRefresh    为 true 时跳过结果缓存读取
     * @param parameterValues 运行时参数；为空时使用默认值
     * @return 不暴露查询定义和数据引用的卡片渲染结果
     */
    public RenderedDashboard render(long dashboardId, boolean forceRefresh,
                                    Map<String, Object> parameterValues) {
        DashboardEntity dashboard = requireDashboard(dashboardId);
        permissionService.require("DASHBOARD", dashboardId, dashboard.getOwnerId(), "READ");
        Map<String, Object> values = mergeDefaults(dashboard.getConfigJson(), parameterValues);
        return new RenderedDashboard(dashboard.getId(), dashboard.getName(), dashboard.getConfigJson(),
                permissionService.accessLevel("DASHBOARD", dashboardId),
                executeCards(dashboardId, forceRefresh, values, dashboard.getConfigJson()));
    }

    /**
     * 以仪表盘所有者身份渲染，供公开/嵌入匿名访问使用（仅默认参数）。
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
            Map<String, Object> values = mergeDefaults(dashboard.getConfigJson(), null);
            return new RenderedDashboard(dashboard.getId(), dashboard.getName(), dashboard.getConfigJson(),
                    "READ", executeCards(dashboardId, false, values, dashboard.getConfigJson()));
        } finally {
            SecurityContextHolder.setContext(originalContext);
        }
    }

    /**
     * 以图表所有者身份执行查询并返回脱敏结果。
     *
     * @param chartId 图表标识
     * @return 图表名称与查询结果
     */
    public RenderedQuestion renderQuestionAsOwner(long chartId) {
        ChartEntity chart = chartMapper.selectById(chartId);
        if (chart == null || chart.getDeletedAt() != null) {
            throw new BusinessException(404, "图表不存在");
        }
        Instant deadline = Instant.now().plus(Duration.ofSeconds(queryProperties.timeoutSeconds() + 5L));
        var originalContext = SecurityContextHolder.getContext();
        var ownerContext = SecurityContextHolder.createEmptyContext();
        try {
            ownerContext.setAuthentication(authenticationService.load(chart.getOwnerId()));
            SecurityContextHolder.setContext(ownerContext);
            JdbcQueryExecutor.QueryResult result = resolveChartResult(chart, "[]", Map.of(), Map.of(),
                    false, deadline);
            return new RenderedQuestion(chart.getId(), chart.getName(), chart.getChartType(),
                    chart.getConfigJson(), result, null);
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
     * 供已完成自身授权判断的后台刷新流程执行全部卡片查询（强制绕过缓存读取并回写）。
     *
     * @param dashboardId 仪表盘标识
     * @return 卡片渲染结果
     */
    public List<RenderedCard> executeCards(long dashboardId) {
        DashboardEntity dashboard = requireDashboard(dashboardId);
        Map<String, Object> values = mergeDefaults(dashboard.getConfigJson(), null);
        return executeCards(dashboardId, true, values, dashboard.getConfigJson());
    }

    /**
     * 执行全部卡片查询。
     *
     * @param dashboardId     仪表盘标识
     * @param forceRefresh    为 true 时跳过结果缓存读取
     * @param parameterValues 运行时参数
     * @param configJson      仪表盘配置
     * @return 卡片渲染结果
     */
    public List<RenderedCard> executeCards(long dashboardId, boolean forceRefresh,
                                           Map<String, Object> parameterValues, String configJson) {
        requireDashboard(dashboardId);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(queryProperties.timeoutSeconds() + 5L));
        Map<String, QueryParameterApplier.ParameterMeta> metas = parameterApplier.parseParameterMetas(configJson);
        List<DashboardCardEntity> cards = cardMapper.selectList(Wrappers.<DashboardCardEntity>lambdaQuery()
                .eq(DashboardCardEntity::getDashboardId, dashboardId).orderByAsc(DashboardCardEntity::getId));
        if (cards.isEmpty()) {
            return List.of();
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<RenderedCard>> futures = cards.stream()
                    .map(card -> CompletableFuture.supplyAsync(
                            () -> executeCard(card, deadline, forceRefresh, parameterValues, metas),
                            executor))
                    .toList();
            List<RenderedCard> results = new ArrayList<>(futures.size());
            for (CompletableFuture<RenderedCard> future : futures) {
                results.add(future.join());
            }
            return List.copyOf(results);
        }
    }

    /**
     * 执行单张卡片：加载图表、应用参数绑定并返回脱敏结果。
     *
     * @param card            仪表盘卡片
     * @param deadline        查询等待截止时间
     * @param forceRefresh    为 true 时跳过缓存读取
     * @param parameterValues 运行时参数
     * @param metas           参数元数据
     * @return 卡片渲染结果
     */
    private RenderedCard executeCard(DashboardCardEntity card, Instant deadline, boolean forceRefresh,
                                     Map<String, Object> parameterValues,
                                     Map<String, QueryParameterApplier.ParameterMeta> metas) {
        ChartEntity chart = chartMapper.selectById(card.getChartId());
        if (chart == null || chart.getDeletedAt() != null) {
            return new RenderedCard(card.getId(), card.getTitle(), null,
                    null, card.getLayoutJson(), card.getBindingsJson(), card.getClickActionJson(),
                    null, "图表查询失败");
        }
        var originalContext = SecurityContextHolder.getContext();
        var ownerContext = SecurityContextHolder.createEmptyContext();
        try {
            ownerContext.setAuthentication(authenticationService.load(chart.getOwnerId()));
            SecurityContextHolder.setContext(ownerContext);
            JdbcQueryExecutor.QueryResult result = resolveChartResult(chart,
                    card.getBindingsJson(), parameterValues, metas, forceRefresh, deadline);
            return new RenderedCard(card.getId(), card.getTitle(), chart.getChartType(),
                    chart.getConfigJson(), card.getLayoutJson(), card.getBindingsJson(),
                    card.getClickActionJson(), result, null);
        } catch (BusinessException exception) {
            return new RenderedCard(card.getId(), card.getTitle(), chart.getChartType(),
                    chart.getConfigJson(), card.getLayoutJson(), card.getBindingsJson(),
                    card.getClickActionJson(), null,
                    exception.getMessage() == null ? "图表查询失败" : exception.getMessage());
        } catch (Exception exception) {
            return new RenderedCard(card.getId(), card.getTitle(), chart.getChartType(),
                    chart.getConfigJson(), card.getLayoutJson(), card.getBindingsJson(),
                    card.getClickActionJson(), null, "图表查询配置错误");
        } finally {
            SecurityContextHolder.setContext(originalContext);
        }
    }

    /**
     * 按绑定与参数解析图表查询结果，可走缓存或强制重查。
     *
     * @param chart           图表实体
     * @param bindingsJson    卡片绑定 JSON
     * @param parameterValues 运行时参数
     * @param metas           参数元数据
     * @param forceRefresh    为 true 时跳过缓存读取并回写
     * @param deadline        查询等待截止时间
     * @return 查询结果
     */
    private JdbcQueryExecutor.QueryResult resolveChartResult(ChartEntity chart, String bindingsJson,
                                                             Map<String, Object> parameterValues,
                                                             Map<String, QueryParameterApplier.ParameterMeta> metas,
                                                             boolean forceRefresh, Instant deadline)
            throws Exception {
        List<QueryParameterApplier.Binding> bindings = parameterApplier.parseBindings(bindingsJson);
        if (!forceRefresh) {
            Optional<JdbcQueryExecutor.QueryResult> cached =
                    chartResultCache.get(chart, bindingsJson, parameterValues);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        QueryService.QuerySubmission submission =
                objectMapper.readValue(chart.getQueryJson(), QueryService.QuerySubmission.class);
        QueryService.QuerySubmission applied =
                parameterApplier.apply(submission, bindings, parameterValues, metas);
        String queryId = queryService.submit(applied);
        QueryStateStore.QuerySnapshot snapshot = await(queryId, deadline);
        chartResultCache.put(chart, bindingsJson, parameterValues, snapshot.result());
        return snapshot.result();
    }

    /**
     * 合并仪表盘默认参数与运行时覆盖值。
     *
     * @param configJson 仪表盘配置 JSON
     * @param overrides  运行时覆盖；可为 {@code null}
     * @return 合并后的参数映射
     */
    private Map<String, Object> mergeDefaults(String configJson, Map<String, Object> overrides) {
        Map<String, Object> merged = new LinkedHashMap<>(parameterApplier.defaultParameterValues(configJson));
        if (overrides != null) {
            merged.putAll(overrides);
        }
        return merged;
    }

    /**
     * 不含查询定义和数据源引用的仪表盘渲染结果。
     */
    public record RenderedDashboard(@JsonSerialize(using = ToStringSerializer.class) long id,
                                    String name, String configJson, String accessLevel,
                                    List<RenderedCard> cards) {
    }

    /**
     * 公开/嵌入图表渲染结果。
     */
    public record RenderedQuestion(@JsonSerialize(using = ToStringSerializer.class) long id,
                                   String name, String chartType, String configJson,
                                   JdbcQueryExecutor.QueryResult result, String error) {
    }

    /**
     * 轮询查询状态直至成功、失败或超时。
     *
     * @param queryId  查询任务标识
     * @param deadline 等待截止时间
     * @return 成功完成的快照
     */
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

    /**
     * 加载未删除的仪表盘，不存在时抛出 404。
     *
     * @param id 仪表盘标识
     * @return 仪表盘实体
     */
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
                               String bindingsJson, String clickActionJson,
                               JdbcQueryExecutor.QueryResult result, String error) {
    }
}
