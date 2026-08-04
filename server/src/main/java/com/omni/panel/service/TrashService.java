package com.omni.panel.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;

/**
 * 统一废纸篓列表、恢复与永久删除。
 */
@Service
public class TrashService {
    private final ChartService chartService;
    private final DashboardService dashboardService;
    private final DatasetService datasetService;
    private final MetricService metricService;

    public TrashService(ChartService chartService, DashboardService dashboardService,
                        DatasetService datasetService, MetricService metricService) {
        this.chartService = chartService;
        this.dashboardService = dashboardService;
        this.datasetService = datasetService;
        this.metricService = metricService;
    }

    /**
     * 列出当前用户（管理员全部）已软删资源。
     *
     * @return 废纸篓条目
     */
    public List<TrashItem> list() {
        List<TrashItem> items = new ArrayList<>();
        chartService.listTrash().forEach(item -> items.add(new TrashItem("QUESTION", item.getId(),
                item.getName(), item.getDescription(), item.getDeletedAt(), item.getOwnerId())));
        dashboardService.listTrash().forEach(item -> items.add(new TrashItem("DASHBOARD", item.getId(),
                item.getName(), item.getDescription(), item.getDeletedAt(), item.getOwnerId())));
        datasetService.listTrash().forEach(item -> items.add(new TrashItem("MODEL", item.getId(),
                item.getName(), item.getDescription(), item.getDeletedAt(), item.getOwnerId())));
        metricService.listTrash().forEach(item -> items.add(new TrashItem("METRIC", item.getId(),
                item.getName(), item.getDescription(), item.getDeletedAt(), item.getOwnerId())));
        return List.copyOf(items);
    }

    /**
     * 恢复资源。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     */
    @Transactional
    public void restore(String resourceType, long resourceId) {
        switch (normalize(resourceType)) {
            case "QUESTION", "CHART" -> chartService.restore(resourceId);
            case "DASHBOARD" -> dashboardService.restore(resourceId);
            case "MODEL", "DATASET" -> datasetService.restore(resourceId);
            case "METRIC" -> metricService.restore(resourceId);
            default -> throw new BusinessException("不支持的资源类型");
        }
    }

    /**
     * 永久删除资源。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     */
    @Transactional
    public void purge(String resourceType, long resourceId) {
        switch (normalize(resourceType)) {
            case "QUESTION", "CHART" -> chartService.purge(resourceId);
            case "DASHBOARD" -> dashboardService.purge(resourceId);
            case "MODEL", "DATASET" -> datasetService.purge(resourceId);
            case "METRIC" -> metricService.purge(resourceId);
            default -> throw new BusinessException("不支持的资源类型");
        }
    }

    /**
     * 将资源类型规范为大写字符串。
     *
     * @param resourceType 资源类型
     * @return 大写类型，空输入返回空串
     */
    private String normalize(String resourceType) {
        return resourceType == null ? "" : resourceType.toUpperCase();
    }
}
