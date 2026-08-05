package com.omni.panel.service;

import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.stereotype.Service;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.entity.DatasetEntity;
import com.omni.panel.entity.RecentItemEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.DatasetMapper;
import com.omni.panel.mapper.RecentItemMapper;

/**
 * 记录并查询用户最近访问的图表、仪表盘与模型，并补充展示名称。
 */
@Service
public class RecentService {
    private final RecentItemMapper mapper;
    private final ChartMapper chartMapper;
    private final DashboardMapper dashboardMapper;
    private final DatasetMapper datasetMapper;
    private final PermissionService permissionService;

    /**
     * 注入最近访问持久化、资源 Mapper 与权限服务。
     *
     * @param mapper            最近访问数据访问
     * @param chartMapper       图表数据访问
     * @param dashboardMapper   仪表盘数据访问
     * @param datasetMapper     数据集数据访问
     * @param permissionService 权限服务
     */
    public RecentService(RecentItemMapper mapper, ChartMapper chartMapper,
                         DashboardMapper dashboardMapper, DatasetMapper datasetMapper,
                         PermissionService permissionService) {
        this.mapper = mapper;
        this.chartMapper = chartMapper;
        this.dashboardMapper = dashboardMapper;
        this.datasetMapper = datasetMapper;
        this.permissionService = permissionService;
    }

    /**
     * 记录用户对指定资源的访问。
     *
     * @param userId       用户标识
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     */
    public void touch(long userId, String resourceType, long resourceId) {
        mapper.touch(userId, resourceType, resourceId);
    }

    /**
     * 查询用户最近访问列表，跳过已删除、不可解析或当前无读取权限的资源。
     *
     * @param userId 用户标识
     * @param limit  返回条数上限
     * @return 按访问时间倒序排列的最近项
     */
    public List<RecentView> list(long userId, int limit) {
        int size = limit <= 0 ? 20 : Math.min(limit, 100);
        int fetch = Math.min(100, Math.max(size * 3, size));
        List<RecentView> views = new ArrayList<>();
        for (RecentItemEntity item : mapper.selectList(Wrappers.<RecentItemEntity>lambdaQuery()
                .eq(RecentItemEntity::getUserId, userId)
                .orderByDesc(RecentItemEntity::getVisitedAt)
                .last("LIMIT " + fetch))) {
            RecentView view = resolve(item);
            if (view != null) {
                views.add(view);
                if (views.size() >= size) {
                    break;
                }
            }
        }
        return List.copyOf(views);
    }

    /**
     * 将最近访问记录解析为展示视图；资源已删除、无权限或类型未知时返回 {@code null}。
     *
     * @param item 最近访问记录
     * @return 展示视图，或 {@code null}
     */
    private RecentView resolve(RecentItemEntity item) {
        String type = item.getResourceType() == null ? "" : item.getResourceType().toUpperCase();
        return switch (type) {
            case "QUESTION", "CHART" -> {
                ChartEntity chart = chartMapper.selectById(item.getResourceId());
                if (chart == null || chart.getDeletedAt() != null
                        || !permissionService.canRead("CHART", chart.getId(), chart.getOwnerId())) {
                    yield null;
                }
                yield new RecentView("QUESTION", chart.getId(), chart.getName(),
                        chart.getDescription(), item.getVisitedAt());
            }
            case "DASHBOARD" -> {
                DashboardEntity dashboard = dashboardMapper.selectById(item.getResourceId());
                if (dashboard == null || dashboard.getDeletedAt() != null
                        || !permissionService.canRead("DASHBOARD", dashboard.getId(), dashboard.getOwnerId())) {
                    yield null;
                }
                yield new RecentView("DASHBOARD", dashboard.getId(), dashboard.getName(),
                        dashboard.getDescription(), item.getVisitedAt());
            }
            case "MODEL", "DATASET" -> {
                DatasetEntity dataset = datasetMapper.selectById(item.getResourceId());
                if (dataset == null || dataset.getDeletedAt() != null
                        || !permissionService.canRead("DATASET", dataset.getId(), dataset.getOwnerId())) {
                    yield null;
                }
                yield new RecentView("MODEL", dataset.getId(), dataset.getName(),
                        dataset.getDescription(), item.getVisitedAt());
            }
            default -> null;
        };
    }

    /**
     * 最近访问视图。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param name         展示名称
     * @param description  描述
     * @param visitedAt    访问时间
     */
    public record RecentView(String resourceType,
                             @JsonSerialize(using = ToStringSerializer.class) long resourceId,
                             String name, String description,
                             java.time.LocalDateTime visitedAt) {
    }
}
