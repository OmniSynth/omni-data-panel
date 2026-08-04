package com.omni.panel.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.MetricEntity;
import com.omni.panel.mapper.MetricMapper;

/**
 * 管理指标的创建、查询与软删。
 */
@Service
public class MetricService {
    private static final Set<String> AGGREGATIONS = Set.of("SUM", "COUNT", "AVG", "MIN", "MAX");
    private final MetricMapper mapper;
    private final DatasetService datasetService;
    private final PermissionService permissionService;
    private final CollectionService collectionService;

    public MetricService(MetricMapper mapper, DatasetService datasetService,
                         PermissionService permissionService, @Lazy CollectionService collectionService) {
        this.mapper = mapper;
        this.datasetService = datasetService;
        this.permissionService = permissionService;
        this.collectionService = collectionService;
    }

    /**
     * 查询可读且未删除的指标。
     *
     * @param collectionId 可选集合过滤
     * @return 指标列表
     */
    public List<MetricEntity> list(Long collectionId, Long modelId) {
        var query = Wrappers.<MetricEntity>lambdaQuery().isNull(MetricEntity::getDeletedAt);
        if (collectionId != null) {
            query.eq(MetricEntity::getCollectionId, collectionId);
        }
        if (modelId != null) {
            query.eq(MetricEntity::getModelId, modelId);
        }
        return mapper.selectList(query).stream()
                .filter(metric -> permissionService.canRead("METRIC", metric.getId(), metric.getOwnerId()))
                .toList();
    }

    /**
     * 查询可读且未删除的指标。
     *
     * @param collectionId 可选集合过滤
     * @return 指标列表
     */
    public List<MetricEntity> list(Long collectionId) {
        return list(collectionId, null);
    }

    /**
     * 查询废纸篓中的指标。
     *
     * @return 已软删指标
     */
    public List<MetricEntity> listTrash() {
        AuthenticatedUser user = AuthenticatedUser.current();
        return mapper.selectList(Wrappers.<MetricEntity>lambdaQuery()
                        .isNotNull(MetricEntity::getDeletedAt).orderByDesc(MetricEntity::getDeletedAt))
                .stream()
                .filter(metric -> user.admin() || metric.getOwnerId().equals(user.id()))
                .toList();
    }

    /**
     * 获取指标并校验权限。
     *
     * @param id         指标标识
     * @param permission 权限
     * @return 指标
     */
    public MetricEntity require(long id, String permission) {
        MetricEntity metric = mapper.selectById(id);
        if (metric == null || metric.getDeletedAt() != null) {
            throw new BusinessException(404, "指标不存在");
        }
        permissionService.require("METRIC", id, metric.getOwnerId(), permission);
        return metric;
    }

    /**
     * 创建指标；创建权限复用 dataset:create。
     *
     * @param name           名称
     * @param description    描述
     * @param modelId        模型标识
     * @param expressionJson 表达式 JSON
     * @param aggregation    聚合方式
     * @param collectionId   集合标识
     * @return 已创建指标
     */
    @Transactional
    public MetricEntity create(String name, String description, long modelId, String expressionJson,
                               String aggregation, Long collectionId) {
        AuthenticatedUser user = AuthenticatedUser.current();
        datasetService.require(modelId, "READ");
        String agg = aggregation.toUpperCase();
        if (!AGGREGATIONS.contains(agg)) {
            throw new BusinessException("聚合方式不合法");
        }
        MetricEntity metric = new MetricEntity();
        metric.setName(name);
        metric.setDescription(description);
        metric.setModelId(modelId);
        metric.setExpressionJson(expressionJson);
        metric.setAggregation(agg);
        metric.setOwnerId(user.id());
        metric.setCollectionId(resolveCollectionId(collectionId, user.id()));
        metric.setCreatedAt(LocalDateTime.now());
        metric.setUpdatedAt(metric.getCreatedAt());
        mapper.insert(metric);
        return metric;
    }

    /**
     * 更新指标。
     *
     * @param id             指标标识
     * @param name           名称
     * @param description    描述
     * @param modelId        模型标识
     * @param expressionJson 表达式
     * @param aggregation    聚合
     * @param collectionId   集合
     * @return 已更新指标
     */
    @Transactional
    public MetricEntity update(long id, String name, String description, long modelId,
                               String expressionJson, String aggregation, Long collectionId) {
        MetricEntity metric = require(id, "WRITE");
        datasetService.require(modelId, "READ");
        String agg = aggregation.toUpperCase();
        if (!AGGREGATIONS.contains(agg)) {
            throw new BusinessException("聚合方式不合法");
        }
        metric.setName(name);
        metric.setDescription(description);
        metric.setModelId(modelId);
        metric.setExpressionJson(expressionJson);
        metric.setAggregation(agg);
        if (collectionId != null) {
            collectionService.requireReadable(collectionId);
            metric.setCollectionId(collectionId);
        }
        metric.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(metric);
        return metric;
    }

    /**
     * 软删除指标。
     *
     * @param id 指标标识
     */
    @Transactional
    public void softDelete(long id) {
        MetricEntity metric = require(id, "WRITE");
        metric.setDeletedAt(LocalDateTime.now());
        metric.setUpdatedAt(metric.getDeletedAt());
        mapper.updateById(metric);
    }

    /**
     * 恢复指标。
     *
     * @param id 指标标识
     */
    @Transactional
    public void restore(long id) {
        MetricEntity metric = requireTrashed(id);
        metric.setDeletedAt(null);
        metric.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(metric);
    }

    /**
     * 永久删除指标。
     *
     * @param id 指标标识
     */
    @Transactional
    public void purge(long id) {
        MetricEntity metric = requireTrashed(id);
        mapper.deleteById(metric.getId());
    }

    /**
     * 加载废纸篓中的指标并校验当前用户操作权限。
     *
     * @param id 指标标识
     * @return 已软删的指标
     */
    private MetricEntity requireTrashed(long id) {
        MetricEntity metric = mapper.selectById(id);
        AuthenticatedUser user = AuthenticatedUser.current();
        if (metric == null || metric.getDeletedAt() == null) {
            throw new BusinessException(404, "废纸篓中不存在该指标");
        }
        if (!user.admin() && !metric.getOwnerId().equals(user.id())) {
            throw new BusinessException(403, "无权操作该指标");
        }
        return metric;
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
