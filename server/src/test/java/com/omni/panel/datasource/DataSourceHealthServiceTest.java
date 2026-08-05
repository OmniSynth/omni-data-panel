package com.omni.panel.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.omni.panel.service.DataSourceHealthService;

class DataSourceHealthServiceTest {
    @Test
    void 可用且建池判定为UP() {
        var probe = new DataSourceRegistry.HealthProbe(
                true, true, 12L, null,
                new DataSourceRegistry.PoolMetrics(5, 1, 1, 1, 2, 0)
        );
        assertThat(DataSourceHealthService.resolveHealth(probe)).isEqualTo("UP");
    }

    @Test
    void 未建池但可用判定为COLD() {
        var probe = new DataSourceRegistry.HealthProbe(
                true, false, 20L, null,
                DataSourceRegistry.PoolMetrics.empty(null)
        );
        assertThat(DataSourceHealthService.resolveHealth(probe)).isEqualTo("COLD");
    }

    @Test
    void 不可用判定为DOWN() {
        var probe = new DataSourceRegistry.HealthProbe(
                false, true, 80L, "连接超时",
                new DataSourceRegistry.PoolMetrics(5, 1, 0, 0, 0, 0)
        );
        assertThat(DataSourceHealthService.resolveHealth(probe)).isEqualTo("DOWN");
    }

    @Test
    void 延迟过高或等待线程判定为DEGRADED() {
        var slow = new DataSourceRegistry.HealthProbe(
                true, true, DataSourceHealthService.DEGRADED_LATENCY_MS, null,
                new DataSourceRegistry.PoolMetrics(5, 1, 1, 1, 2, 0)
        );
        var waiting = new DataSourceRegistry.HealthProbe(
                true, true, 10L, null,
                new DataSourceRegistry.PoolMetrics(5, 1, 5, 0, 5, 2)
        );
        assertThat(DataSourceHealthService.resolveHealth(slow)).isEqualTo("DEGRADED");
        assertThat(DataSourceHealthService.resolveHealth(waiting)).isEqualTo("DEGRADED");
    }
}
