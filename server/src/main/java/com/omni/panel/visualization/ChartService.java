package com.omni.panel.visualization;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.collection.CollectionService;
import com.omni.panel.common.BusinessException;
import com.omni.panel.dataset.DatasetService;
import com.omni.panel.datasource.DataSourceService;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.permission.PermissionService;
import com.omni.panel.query.QueryService;
import com.omni.panel.query.SqlPolicyGuard;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理问题（图表）的读写、集合归属与软删生命周期。
 */
@Service
public class ChartService {
    private final ChartMapper mapper;
    private final DashboardCardMapper cardMapper;
    private final DatasetService datasetService;
    private final DataSourceService dataSourceService;
    private final PermissionService permissionService;
    private final SqlPolicyGuard sqlPolicyGuard;
    private final DialectRegistry dialectRegistry;
    private final ObjectMapper objectMapper;
    private final CollectionService collectionService;

    public ChartService(ChartMapper mapper, DashboardCardMapper cardMapper, DatasetService datasetService,
                        DataSourceService dataSourceService, PermissionService permissionService,
                        SqlPolicyGuard sqlPolicyGuard, DialectRegistry dialectRegistry, ObjectMapper objectMapper,
                        @Lazy CollectionService collectionService) {
        this.mapper = mapper;
        this.cardMapper = cardMapper;
        this.datasetService = datasetService;
        this.dataSourceService = dataSourceService;
        this.permissionService = permissionService;
        this.sqlPolicyGuard = sqlPolicyGuard;
        this.dialectRegistry = dialectRegistry;
        this.objectMapper = objectMapper;
        this.collectionService = collectionService;
    }

    /**
     * 查询当前用户可读且未删除的问题。
     *
     * @return 问题列表
     */
    public List<ChartEntity> listReadable() {
        return mapper.selectList(Wrappers.<ChartEntity>lambdaQuery().isNull(ChartEntity::getDeletedAt))
            .stream()
            .filter(chart -> permissionService.canRead("CHART", chart.getId(), chart.getOwnerId()))
            .toList();
    }

    /**
     * 查询废纸篓中的问题。
     *
     * @return 已软删问题
     */
    public List<ChartEntity> listTrash() {
        AuthenticatedUser user = AuthenticatedUser.current();
        return mapper.selectList(Wrappers.<ChartEntity>lambdaQuery()
                .isNotNull(ChartEntity::getDeletedAt).orderByDesc(ChartEntity::getDeletedAt))
            .stream()
            .filter(chart -> user.admin() || chart.getOwnerId().equals(user.id()))
            .toList();
    }

    /**
     * 获取问题并校验权限。
     *
     * @param id 问题标识
     * @param permission 权限
     * @return 问题
     */
    public ChartEntity require(long id, String permission) {
        ChartEntity chart = mapper.selectById(id);
        if (chart == null || chart.getDeletedAt() != null) {
            throw new BusinessException(404, "问题不存在");
        }
        permissionService.require("CHART", id, chart.getOwnerId(), permission);
        return chart;
    }

    /**
     * 创建问题。
     *
     * @param request 保存参数
     * @return 已创建问题
     */
    @Transactional
    public ChartEntity create(SaveRequest request) {
        validate(request);
        AuthenticatedUser user = AuthenticatedUser.current();
        ChartEntity chart = apply(new ChartEntity(), request);
        chart.setOwnerId(user.id());
        chart.setCollectionId(resolveCollectionId(request.collectionId(), user.id()));
        chart.setUpdatedAt(LocalDateTime.now());
        mapper.insert(chart);
        return chart;
    }

    /**
     * 更新问题。
     *
     * @param id 问题标识
     * @param request 保存参数
     * @return 已更新问题
     */
    @Transactional
    public ChartEntity update(long id, SaveRequest request) {
        ChartEntity chart = require(id, "WRITE");
        validate(request);
        apply(chart, request);
        if (request.collectionId() != null) {
            collectionService.requireReadable(request.collectionId());
            chart.setCollectionId(request.collectionId());
        }
        chart.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(chart);
        return chart;
    }

    /**
     * 软删除问题。
     *
     * @param id 问题标识
     */
    @Transactional
    public void softDelete(long id) {
        ChartEntity chart = require(id, "WRITE");
        chart.setDeletedAt(LocalDateTime.now());
        chart.setUpdatedAt(chart.getDeletedAt());
        mapper.updateById(chart);
    }

    /**
     * 恢复已软删问题。
     *
     * @param id 问题标识
     */
    @Transactional
    public void restore(long id) {
        ChartEntity chart = requireTrashed(id);
        chart.setDeletedAt(null);
        chart.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(chart);
    }

    /**
     * 永久删除问题；若仍被仪表盘卡片引用则拒绝。
     *
     * @param id 问题标识
     */
    @Transactional
    public void purge(long id) {
        ChartEntity chart = requireTrashed(id);
        long refs = cardMapper.selectCount(Wrappers.<DashboardCardEntity>lambdaQuery()
            .eq(DashboardCardEntity::getChartId, id));
        if (refs > 0) {
            throw new BusinessException("问题仍被仪表盘卡片引用，无法永久删除");
        }
        permissionService.deleteResource("CHART", id);
        mapper.deleteById(chart.getId());
    }

    private ChartEntity requireTrashed(long id) {
        ChartEntity chart = mapper.selectById(id);
        AuthenticatedUser user = AuthenticatedUser.current();
        if (chart == null || chart.getDeletedAt() == null) {
            throw new BusinessException(404, "废纸篓中不存在该问题");
        }
        if (!user.admin() && !chart.getOwnerId().equals(user.id())) {
            throw new BusinessException(403, "无权操作该问题");
        }
        return chart;
    }

    private long resolveCollectionId(Long collectionId, long userId) {
        if (collectionId != null) {
            collectionService.requireReadable(collectionId);
            return collectionId;
        }
        return collectionService.ensurePersonalCollection(userId).getId();
    }

    private ChartEntity apply(ChartEntity chart, SaveRequest request) {
        chart.setName(request.name());
        chart.setDescription(request.description());
        chart.setDatasetId(request.datasetId());
        chart.setDataSourceId(request.dataSourceId());
        chart.setQueryJson(request.queryJson());
        chart.setChartType(request.chartType());
        chart.setConfigJson(request.configJson());
        return chart;
    }

    private void validate(SaveRequest request) {
        if (request.chartType() == null || request.chartType().isBlank()) {
            throw new BusinessException("图表类型不能为空");
        }
        QueryService.QuerySubmission submission;
        try {
            submission = objectMapper.readValue(request.queryJson(), QueryService.QuerySubmission.class);
        } catch (Exception exception) {
            throw new BusinessException("查询定义 JSON 无效");
        }
        boolean semantic = submission.query() != null;
        boolean raw = submission.query() == null && submission.sourceId() != null
            && submission.sql() != null && !submission.sql().isBlank();
        if (semantic == raw) {
            throw new BusinessException("问题必须且只能使用一种查询模式");
        }
        if (semantic) {
            if (request.datasetId() == null || request.dataSourceId() != null
                || submission.sourceId() != null || submission.sql() != null
                || !request.datasetId().equals(submission.query().datasetId())) {
                throw new BusinessException("语义查询与模型引用不匹配");
            }
            var dataset = datasetService.require(request.datasetId(), "READ");
            dataSourceService.require(dataset.getDataSourceId(), "READ");
            return;
        }
        AuthenticatedUser user = AuthenticatedUser.current();
        if (!user.admin() && !user.permissions().contains("query:raw")) {
            throw new BusinessException(403, "缺少原生 SQL 查询权限");
        }
        if (request.datasetId() != null || request.dataSourceId() == null
            || !request.dataSourceId().equals(submission.sourceId())) {
            throw new BusinessException("原生 SQL 查询与数据源引用不匹配");
        }
        var source = dataSourceService.require(request.dataSourceId(), "READ");
        sqlPolicyGuard.validate(submission.sql(), dialectRegistry.resolve(source));
    }

    /**
     * 问题保存请求。
     */
    public record SaveRequest(String name, String description, Long datasetId, Long dataSourceId,
                              String queryJson, String chartType, String configJson, Long collectionId) {}
}
