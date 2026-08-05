package com.omni.panel.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.CollectionEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.entity.DatasetEntity;
import com.omni.panel.entity.MetricEntity;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.CollectionMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.DatasetMapper;
import com.omni.panel.mapper.MetricMapper;
import com.omni.panel.mapper.UserMapper;

/**
 * 管理集合树、个人集合以及集合内资源聚合与迁移。
 */
@Service
public class CollectionService {
    private final CollectionMapper collectionMapper;
    private final ChartMapper chartMapper;
    private final DashboardMapper dashboardMapper;
    private final DatasetMapper datasetMapper;
    private final MetricMapper metricMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;

    public CollectionService(CollectionMapper collectionMapper, ChartMapper chartMapper,
                             DashboardMapper dashboardMapper, DatasetMapper datasetMapper,
                             MetricMapper metricMapper, UserMapper userMapper,
                             PermissionService permissionService) {
        this.collectionMapper = collectionMapper;
        this.chartMapper = chartMapper;
        this.dashboardMapper = dashboardMapper;
        this.datasetMapper = datasetMapper;
        this.metricMapper = metricMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
    }

    /**
     * 确保当前用户存在个人根集合并返回。
     *
     * @param userId 用户标识
     * @return 个人根集合
     */
    @Transactional
    public CollectionEntity ensurePersonalCollection(long userId) {
        CollectionEntity existing = collectionMapper.selectOne(Wrappers.<CollectionEntity>lambdaQuery()
                .eq(CollectionEntity::getPersonalOwnerId, userId));
        if (existing != null) {
            return existing;
        }
        CollectionEntity collection = new CollectionEntity();
        collection.setName("你的个人集合");
        collection.setPersonalOwnerId(userId);
        collection.setOwnerId(userId);
        collection.setArchived(false);
        collection.setCreatedAt(LocalDateTime.now());
        collection.setUpdatedAt(collection.getCreatedAt());
        collectionMapper.insert(collection);
        return collection;
    }

    /**
     * 返回集合树；访问时自动确保当前用户个人集合存在。
     *
     * @return 根节点列表
     */
    @Transactional
    public List<CollectionNode> tree() {
        AuthenticatedUser user = AuthenticatedUser.current();
        ensurePersonalCollection(user.id());
        List<CollectionEntity> all = collectionMapper.selectList(Wrappers.<CollectionEntity>lambdaQuery()
                .eq(CollectionEntity::getArchived, false)
                .orderByAsc(CollectionEntity::getId));
        List<CollectionEntity> visible = all.stream()
                .filter(item -> permissionService.canRead("COLLECTION", item.getId(), item.getOwnerId()))
                .toList();
        Map<Long, List<CollectionEntity>> children = visible.stream()
                .filter(item -> item.getParentId() != null)
                .collect(Collectors.groupingBy(CollectionEntity::getParentId));
        long currentUserId = user.id();
        return visible.stream()
                .filter(item -> item.getParentId() == null || visible.stream()
                        .noneMatch(candidate -> candidate.getId().equals(item.getParentId())))
                .map(item -> toNode(item, children, currentUserId))
                .toList();
    }

    /**
     * 创建非个人集合。
     *
     * @param name        名称
     * @param description 描述
     * @param parentId    父集合标识
     * @return 已创建集合
     */
    @Transactional
    public CollectionEntity create(String name, String description, Long parentId) {
        AuthenticatedUser user = AuthenticatedUser.current();
        if (parentId != null) {
            requireReadable(parentId);
        }
        CollectionEntity collection = new CollectionEntity();
        collection.setName(name);
        collection.setDescription(description);
        collection.setParentId(parentId);
        collection.setOwnerId(user.id());
        collection.setArchived(false);
        collection.setCreatedAt(LocalDateTime.now());
        collection.setUpdatedAt(collection.getCreatedAt());
        collectionMapper.insert(collection);
        return collection;
    }

    /**
     * 更新集合名称与描述。
     *
     * @param id          集合标识
     * @param name        名称
     * @param description 描述
     * @return 已更新集合
     */
    @Transactional
    public CollectionEntity update(long id, String name, String description) {
        CollectionEntity collection = requireWritable(id);
        collection.setName(name);
        collection.setDescription(description);
        collection.setUpdatedAt(LocalDateTime.now());
        collectionMapper.updateById(collection);
        return collection;
    }

    /**
     * 删除无子集合且无内容的集合；个人根集合不可删。
     *
     * @param id 集合标识
     */
    @Transactional
    public void delete(long id) {
        CollectionEntity collection = requireWritable(id);
        if (collection.getPersonalOwnerId() != null) {
            throw new BusinessException("个人根集合不可删除");
        }
        long children = collectionMapper.selectCount(Wrappers.<CollectionEntity>lambdaQuery()
                .eq(CollectionEntity::getParentId, id));
        if (children > 0) {
            throw new BusinessException("集合仍有子集合，无法删除");
        }
        if (!items(id).isEmpty()) {
            throw new BusinessException("集合仍有内容，无法删除");
        }
        permissionService.deleteResource("COLLECTION", id);
        collectionMapper.deleteById(id);
    }

    /**
     * 聚合集合下未删除资源。
     *
     * @param collectionId 集合标识
     * @return 资源条目列表
     */
    public List<CollectionItem> items(long collectionId) {
        requireReadable(collectionId);
        List<CollectionItem> items = new ArrayList<>();
        chartMapper.selectList(Wrappers.<ChartEntity>lambdaQuery()
                        .eq(ChartEntity::getCollectionId, collectionId).isNull(ChartEntity::getDeletedAt))
                .stream().filter(chart -> permissionService.canRead("CHART", chart.getId(), chart.getOwnerId()))
                .forEach(chart -> items.add(new CollectionItem(chart.getId(), "QUESTION", chart.getName(),
                        chart.getDescription(), chart.getUpdatedAt(), chart.getOwnerId())));
        dashboardMapper.selectList(Wrappers.<DashboardEntity>lambdaQuery()
                        .eq(DashboardEntity::getCollectionId, collectionId).isNull(DashboardEntity::getDeletedAt))
                .stream().filter(dash -> permissionService.canRead("DASHBOARD", dash.getId(), dash.getOwnerId()))
                .forEach(dash -> items.add(new CollectionItem(dash.getId(), "DASHBOARD", dash.getName(),
                        dash.getDescription(), dash.getUpdatedAt(), dash.getOwnerId())));
        datasetMapper.selectList(Wrappers.<DatasetEntity>lambdaQuery()
                        .eq(DatasetEntity::getCollectionId, collectionId).isNull(DatasetEntity::getDeletedAt))
                .stream().filter(model -> permissionService.canRead("DATASET", model.getId(), model.getOwnerId()))
                .forEach(model -> items.add(new CollectionItem(model.getId(), "MODEL", model.getName(),
                        model.getDescription(), model.getUpdatedAt(), model.getOwnerId())));
        metricMapper.selectList(Wrappers.<MetricEntity>lambdaQuery()
                        .eq(MetricEntity::getCollectionId, collectionId).isNull(MetricEntity::getDeletedAt))
                .stream().filter(metric -> permissionService.canRead("METRIC", metric.getId(), metric.getOwnerId()))
                .forEach(metric -> items.add(new CollectionItem(metric.getId(), "METRIC", metric.getName(),
                        metric.getDescription(), metric.getUpdatedAt(), metric.getOwnerId())));
        return List.copyOf(items);
    }

    /**
     * 将资源迁入目标集合。
     *
     * @param resourceType 资源类型 QUESTION/DASHBOARD/MODEL/METRIC
     * @param resourceId   资源标识
     * @param collectionId 目标集合标识
     */
    @Transactional
    public void move(String resourceType, long resourceId, long collectionId) {
        requireWritable(collectionId);
        String type = resourceType == null ? "" : resourceType.toUpperCase();
        switch (type) {
            case "QUESTION", "CHART" -> {
                ChartEntity chart = chartMapper.selectById(resourceId);
                if (chart == null || chart.getDeletedAt() != null) {
                    throw new BusinessException(404, "图表不存在");
                }
                permissionService.require("CHART", resourceId, chart.getOwnerId(), "WRITE");
                chart.setCollectionId(collectionId);
                chart.setUpdatedAt(LocalDateTime.now());
                chartMapper.updateById(chart);
            }
            case "DASHBOARD" -> {
                DashboardEntity dashboard = dashboardMapper.selectById(resourceId);
                if (dashboard == null || dashboard.getDeletedAt() != null) {
                    throw new BusinessException(404, "仪表盘不存在");
                }
                permissionService.require("DASHBOARD", resourceId, dashboard.getOwnerId(), "WRITE");
                dashboard.setCollectionId(collectionId);
                dashboard.setUpdatedAt(LocalDateTime.now());
                dashboardMapper.updateById(dashboard);
            }
            case "MODEL", "DATASET" -> {
                DatasetEntity dataset = datasetMapper.selectById(resourceId);
                if (dataset == null || dataset.getDeletedAt() != null) {
                    throw new BusinessException(404, "模型不存在");
                }
                permissionService.require("DATASET", resourceId, dataset.getOwnerId(), "WRITE");
                dataset.setCollectionId(collectionId);
                dataset.setUpdatedAt(LocalDateTime.now());
                datasetMapper.updateById(dataset);
            }
            case "METRIC" -> {
                MetricEntity metric = metricMapper.selectById(resourceId);
                if (metric == null || metric.getDeletedAt() != null) {
                    throw new BusinessException(404, "指标不存在");
                }
                permissionService.require("METRIC", resourceId, metric.getOwnerId(), "WRITE");
                metric.setCollectionId(collectionId);
                metric.setUpdatedAt(LocalDateTime.now());
                metricMapper.updateById(metric);
            }
            default -> throw new BusinessException("不支持的资源类型");
        }
    }

    /**
     * 校验集合可读并返回实体。
     *
     * @param id 集合标识
     * @return 集合
     */
    public CollectionEntity requireReadable(long id) {
        CollectionEntity collection = collectionMapper.selectById(id);
        if (collection == null || Boolean.TRUE.equals(collection.getArchived())) {
            throw new BusinessException(404, "集合不存在");
        }
        permissionService.require("COLLECTION", id, collection.getOwnerId(), "READ");
        return collection;
    }

    /**
     * 校验集合可读且当前用户具备写权限。
     *
     * @param id 集合标识
     * @return 集合实体
     */
    private CollectionEntity requireWritable(long id) {
        CollectionEntity collection = requireReadable(id);
        permissionService.require("COLLECTION", id, collection.getOwnerId(), "WRITE");
        return collection;
    }

    /**
     * 递归构建集合树节点。
     *
     * @param entity        当前集合
     * @param children      父标识到子集合列表的映射
     * @param currentUserId 当前用户标识
     * @return 含子节点的树节点
     */
    private CollectionNode toNode(CollectionEntity entity, Map<Long, List<CollectionEntity>> children,
                                  long currentUserId) {
        List<CollectionNode> childNodes = children.getOrDefault(entity.getId(), List.of()).stream()
                .map(child -> toNode(child, children, currentUserId)).toList();
        return new CollectionNode(entity.getId(), displayName(entity, currentUserId), entity.getDescription(),
                entity.getParentId(), entity.getPersonalOwnerId(), entity.getOwnerId(), childNodes);
    }

    /**
     * 个人集合对外展示名：本人为「你的个人集合」，他人为「{显示名} 的个人集合」。
     */
    private String displayName(CollectionEntity entity, long currentUserId) {
        Long personalOwnerId = entity.getPersonalOwnerId();
        if (personalOwnerId == null) {
            return entity.getName();
        }
        if (personalOwnerId.equals(currentUserId)) {
            return "你的个人集合";
        }
        SysUser owner = userMapper.selectById(personalOwnerId);
        String label = "用户" + personalOwnerId;
        if (owner != null) {
            if (owner.getDisplayName() != null && !owner.getDisplayName().isBlank()) {
                label = owner.getDisplayName().trim();
            } else if (owner.getUsername() != null && !owner.getUsername().isBlank()) {
                label = owner.getUsername().trim();
            }
        }
        return label + " 的个人集合";
    }

    /**
     * 集合树节点。
     */
    public record CollectionNode(@JsonSerialize(using = ToStringSerializer.class) long id,
                                 String name, String description,
                                 @JsonSerialize(using = ToStringSerializer.class) Long parentId,
                                 @JsonSerialize(using = ToStringSerializer.class) Long personalOwnerId,
                                 @JsonSerialize(using = ToStringSerializer.class) long ownerId,
                                 List<CollectionNode> children) {
    }

    /**
     * 集合内容条目。
     */
    public record CollectionItem(@JsonSerialize(using = ToStringSerializer.class) long id,
                                 String type, String name, String description,
                                 java.time.LocalDateTime updatedAt,
                                 @JsonSerialize(using = ToStringSerializer.class) long ownerId) {
    }
}
