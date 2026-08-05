package com.omni.panel.service;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.DashboardCardEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.DashboardCardMapper;
import com.omni.panel.query.SqlPolicyGuard;

/**
 * 管理图表的读写、集合归属与软删生命周期。
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
     * 查询当前用户可读且未删除的图表。
     *
     * @return 图表列表
     */
    public List<ChartEntity> listReadable() {
        return mapper.selectList(Wrappers.<ChartEntity>lambdaQuery().isNull(ChartEntity::getDeletedAt))
                .stream()
                .filter(chart -> permissionService.canRead("CHART", chart.getId(), chart.getOwnerId()))
                .toList();
    }

    /**
     * 查询废纸篓中的图表。
     *
     * @return 已软删图表
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
     * 获取图表并校验权限。
     *
     * @param id         图表标识
     * @param permission 权限
     * @return 图表
     */
    public ChartEntity require(long id, String permission) {
        ChartEntity chart = mapper.selectById(id);
        if (chart == null || chart.getDeletedAt() != null) {
            throw new BusinessException(404, "图表不存在");
        }
        permissionService.require("CHART", id, chart.getOwnerId(), permission);
        return chart;
    }

    /**
     * 创建图表。
     *
     * @param request 保存参数
     * @return 已创建图表
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
     * 更新图表。
     *
     * @param id      图表标识
     * @param request 保存参数
     * @return 已更新图表
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
     * 软删除图表。
     *
     * @param id 图表标识
     */
    @Transactional
    public void softDelete(long id) {
        ChartEntity chart = require(id, "WRITE");
        chart.setDeletedAt(LocalDateTime.now());
        chart.setUpdatedAt(chart.getDeletedAt());
        mapper.updateById(chart);
    }

    /**
     * 恢复已软删图表。
     *
     * @param id 图表标识
     */
    @Transactional
    public void restore(long id) {
        ChartEntity chart = requireTrashed(id);
        chart.setDeletedAt(null);
        chart.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(chart);
    }

    /**
     * 永久删除图表；若仍被仪表盘卡片引用则拒绝。
     *
     * @param id 图表标识
     */
    @Transactional
    public void purge(long id) {
        ChartEntity chart = requireTrashed(id);
        long refs = cardMapper.selectCount(Wrappers.<DashboardCardEntity>lambdaQuery()
                .eq(DashboardCardEntity::getChartId, id));
        if (refs > 0) {
            throw new BusinessException("图表仍被仪表盘卡片引用，无法永久删除");
        }
        permissionService.deleteResource("CHART", id);
        mapper.deleteById(chart.getId());
    }

    /**
     * 加载废纸篓中的图表并校验当前用户操作权限。
     *
     * @param id 图表标识
     * @return 已软删的图表
     */
    private ChartEntity requireTrashed(long id) {
        ChartEntity chart = mapper.selectById(id);
        AuthenticatedUser user = AuthenticatedUser.current();
        if (chart == null || chart.getDeletedAt() == null) {
            throw new BusinessException(404, "废纸篓中不存在该图表");
        }
        if (!user.admin() && !chart.getOwnerId().equals(user.id())) {
            throw new BusinessException(403, "无权操作该图表");
        }
        return chart;
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

    /**
     * 将保存请求字段写入图表实体。
     *
     * @param chart   目标图表实体
     * @param request 保存参数
     * @return 已填充的图表实体
     */
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

    /**
     * 校验图表类型、查询模式及关联的数据源或模型引用。
     *
     * @param request 保存参数
     */
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
            throw new BusinessException("图表必须且只能使用一种查询模式");
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
     * 图表保存请求。
     */
    public record SaveRequest(String name, String description, Long datasetId, Long dataSourceId,
                              String queryJson, String chartType, String configJson, Long collectionId) {
    }
}
