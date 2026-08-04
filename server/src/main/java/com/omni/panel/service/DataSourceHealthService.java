package com.omni.panel.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.stereotype.Service;
import com.omni.panel.datasource.DataSourceRegistry;
import com.omni.panel.entity.DataSourceEntity;
import com.omni.panel.mapper.DataSourceMapper;

/**
 * 汇总分析数据源连接池运行指标与可用性探测结果。
 */
@Service
public class DataSourceHealthService {
    /**
     * 延迟超过该阈值（毫秒）视为亚健康。
     */
    public static final long DEGRADED_LATENCY_MS = 1_000L;

    private final DataSourceMapper mapper;
    private final DataSourceRegistry registry;
    private final DataSourceService dataSourceService;

    public DataSourceHealthService(DataSourceMapper mapper, DataSourceRegistry registry,
                                   DataSourceService dataSourceService) {
        this.mapper = mapper;
        this.registry = registry;
        this.dataSourceService = dataSourceService;
    }

    /**
     * 探测全部数据源的健康快照。
     */
    public HealthOverview overview() {
        List<DataSourceEntity> sources = mapper.selectList(null).stream()
                .map(dataSourceService::ensureConnectionFields)
                .sorted(Comparator.comparing(DataSourceEntity::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        List<HealthItem> items = new ArrayList<>(sources.size());
        int up = 0;
        int down = 0;
        int degraded = 0;
        int cold = 0;
        int disabled = 0;
        for (DataSourceEntity source : sources) {
            HealthItem item = inspect(source);
            items.add(item);
            switch (item.health()) {
                case "UP" -> up++;
                case "DOWN" -> down++;
                case "DEGRADED" -> degraded++;
                case "COLD" -> cold++;
                case "DISABLED" -> disabled++;
                default -> {
                }
            }
        }
        return new HealthOverview(
                Instant.now().toString(),
                sources.size(),
                up,
                degraded,
                down,
                cold,
                disabled,
                items
        );
    }

    /**
     * 探测单个数据源的健康状态并组装展示项。
     *
     * @param source 数据源配置
     * @return 健康项
     */
    private HealthItem inspect(DataSourceEntity source) {
        long checkedAtMs = System.currentTimeMillis();
        if (!"ACTIVE".equalsIgnoreCase(source.getStatus())) {
            return new HealthItem(
                    source.getId(),
                    source.getName(),
                    source.getHost(),
                    source.getPort(),
                    source.getDefaultDatabase(),
                    source.getStatus(),
                    "DISABLED",
                    false,
                    null,
                    "数据源未启用",
                    null, null, null, null, null, null,
                    checkedAtMs
            );
        }
        DataSourceRegistry.HealthProbe probe = registry.probe(source);
        DataSourceRegistry.PoolMetrics metrics = probe.metrics();
        String health = resolveHealth(probe);
        return new HealthItem(
                source.getId(),
                source.getName(),
                source.getHost(),
                source.getPort(),
                source.getDefaultDatabase(),
                source.getStatus(),
                health,
                probe.poolReady(),
                probe.latencyMs(),
                probe.message(),
                metrics.maximumPoolSize(),
                metrics.minimumIdle(),
                metrics.activeConnections(),
                metrics.idleConnections(),
                metrics.totalConnections(),
                metrics.threadsAwaitingConnection(),
                checkedAtMs
        );
    }

    /**
     * 根据探测结果判定健康状态。
     */
    public static String resolveHealth(DataSourceRegistry.HealthProbe probe) {
        if (!probe.available()) {
            return "DOWN";
        }
        DataSourceRegistry.PoolMetrics metrics = probe.metrics();
        boolean saturated = metrics != null
                && metrics.maximumPoolSize() != null
                && metrics.maximumPoolSize() > 0
                && (
                (metrics.activeConnections() != null
                        && metrics.activeConnections() * 10 >= metrics.maximumPoolSize() * 8)
                        || (metrics.threadsAwaitingConnection() != null && metrics.threadsAwaitingConnection() > 0)
        );
        boolean slow = probe.latencyMs() != null && probe.latencyMs() >= DEGRADED_LATENCY_MS;
        if (slow || saturated) {
            return "DEGRADED";
        }
        if (!probe.poolReady()) {
            return "COLD";
        }
        return "UP";
    }

    /**
     * 健康总览。
     */
    public record HealthOverview(
            String checkedAt,
            int total,
            int up,
            int degraded,
            int down,
            int cold,
            int disabled,
            List<HealthItem> items
    ) {
    }

    /**
     * 单个数据源健康项。
     */
    public record HealthItem(
            @JsonSerialize(using = ToStringSerializer.class) Long sourceId,
            String name,
            String host,
            Integer port,
            String defaultDatabase,
            String sourceStatus,
            String health,
            boolean poolReady,
            Long latencyMs,
            String message,
            Integer maximumPoolSize,
            Integer minimumIdle,
            Integer activeConnections,
            Integer idleConnections,
            Integer totalConnections,
            Integer threadsAwaitingConnection,
            long checkedAtMs
    ) {
    }
}
