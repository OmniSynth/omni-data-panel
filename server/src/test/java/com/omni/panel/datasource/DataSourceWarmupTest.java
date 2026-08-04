package com.omni.panel.datasource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSourceWarmupTest {
    @Test
    void 启动时并行预热全部数据源() {
        DataSourceMapper mapper = mock(DataSourceMapper.class);
        DataSourceRegistry registry = mock(DataSourceRegistry.class);
        DataSourceService dataSourceService = mock(DataSourceService.class);
        DataSourceEntity first = source(1L, "销售库");
        DataSourceEntity second = source(2L, "订单库");
        when(mapper.selectList(null)).thenReturn(java.util.List.of(first, second));
        when(registry.contains(1L)).thenReturn(true);
        when(registry.contains(2L)).thenReturn(true);

        new DataSourceWarmup(mapper, registry, dataSourceService).run(null);
        verify(dataSourceService).backfillMissingConnectionFields();

        verify(registry, times(1)).warmUp(first);
        verify(registry, times(1)).warmUp(second);
        assertThat(registry.contains(1L)).isTrue();
    }

    @Test
    void 单个预热失败不阻断其他数据源() {
        DataSourceMapper mapper = mock(DataSourceMapper.class);
        DataSourceRegistry registry = mock(DataSourceRegistry.class);
        DataSourceService dataSourceService = mock(DataSourceService.class);
        DataSourceEntity broken = source(1L, "失效库");
        DataSourceEntity healthy = source(2L, "可用库");
        when(mapper.selectList(null)).thenReturn(java.util.List.of(broken, healthy));
        org.mockito.Mockito.doThrow(new RuntimeException("连接失败")).when(registry).warmUp(broken);
        when(registry.contains(2L)).thenReturn(true);

        new DataSourceWarmup(mapper, registry, dataSourceService).run(null);

        verify(registry).warmUp(broken);
        verify(registry).warmUp(healthy);
    }

    private DataSourceEntity source(long id, String name) {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
