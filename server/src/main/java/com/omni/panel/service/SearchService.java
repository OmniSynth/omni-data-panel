package com.omni.panel.service;

import java.util.ArrayList;
import java.util.List;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.stereotype.Service;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.CollectionEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.entity.DatasetEntity;
import com.omni.panel.entity.MetricEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.CollectionMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.DatasetMapper;
import com.omni.panel.mapper.MetricMapper;

/**
 * 按名称模糊搜索集合与内容资源。
 */
@Service
public class SearchService {
    private final CollectionMapper collectionMapper;
    private final ChartMapper chartMapper;
    private final DashboardMapper dashboardMapper;
    private final DatasetMapper datasetMapper;
    private final MetricMapper metricMapper;
    private final PermissionService permissionService;

    public SearchService(CollectionMapper collectionMapper, ChartMapper chartMapper,
                         DashboardMapper dashboardMapper, DatasetMapper datasetMapper,
                         MetricMapper metricMapper, PermissionService permissionService) {
        this.collectionMapper = collectionMapper;
        this.chartMapper = chartMapper;
        this.dashboardMapper = dashboardMapper;
        this.datasetMapper = datasetMapper;
        this.metricMapper = metricMapper;
        this.permissionService = permissionService;
    }

    /**
     * 模糊搜索未删除资源名称。
     *
     * @param q 关键字
     * @return 统一搜索命中
     */
    public List<SearchHit> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String keyword = q.trim();
        AuthenticatedUser user = AuthenticatedUser.current();
        List<SearchHit> hits = new ArrayList<>();
        collectionMapper.selectList(Wrappers.<CollectionEntity>lambdaQuery()
                        .eq(CollectionEntity::getArchived, false)
                        .like(CollectionEntity::getName, keyword))
                .stream()
                .filter(item -> user.admin() || item.getOwnerId().equals(user.id())
                        || (item.getPersonalOwnerId() != null && item.getPersonalOwnerId().equals(user.id())))
                .forEach(item -> hits.add(new SearchHit(item.getId(), "COLLECTION", item.getName(),
                        item.getDescription())));
        chartMapper.selectList(Wrappers.<ChartEntity>lambdaQuery()
                        .isNull(ChartEntity::getDeletedAt).like(ChartEntity::getName, keyword))
                .stream()
                .filter(item -> permissionService.canRead("CHART", item.getId(), item.getOwnerId()))
                .forEach(item -> hits.add(new SearchHit(item.getId(), "QUESTION", item.getName(),
                        item.getDescription())));
        dashboardMapper.selectList(Wrappers.<DashboardEntity>lambdaQuery()
                        .isNull(DashboardEntity::getDeletedAt).like(DashboardEntity::getName, keyword))
                .stream()
                .filter(item -> permissionService.canRead("DASHBOARD", item.getId(), item.getOwnerId()))
                .forEach(item -> hits.add(new SearchHit(item.getId(), "DASHBOARD", item.getName(),
                        item.getDescription())));
        datasetMapper.selectList(Wrappers.<DatasetEntity>lambdaQuery()
                        .isNull(DatasetEntity::getDeletedAt).like(DatasetEntity::getName, keyword))
                .stream()
                .filter(item -> permissionService.canRead("DATASET", item.getId(), item.getOwnerId()))
                .forEach(item -> hits.add(new SearchHit(item.getId(), "MODEL", item.getName(),
                        item.getDescription())));
        metricMapper.selectList(Wrappers.<MetricEntity>lambdaQuery()
                        .isNull(MetricEntity::getDeletedAt).like(MetricEntity::getName, keyword))
                .stream()
                .filter(item -> permissionService.canRead("METRIC", item.getId(), item.getOwnerId()))
                .forEach(item -> hits.add(new SearchHit(item.getId(), "METRIC", item.getName(),
                        item.getDescription())));
        return List.copyOf(hits);
    }

    /**
     * 统一搜索命中。
     */
    public record SearchHit(@JsonSerialize(using = ToStringSerializer.class) long id,
                            String type, String name, String description) {
    }
}
