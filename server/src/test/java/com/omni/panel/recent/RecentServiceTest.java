package com.omni.panel.recent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.entity.RecentItemEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.DatasetMapper;
import com.omni.panel.mapper.RecentItemMapper;
import com.omni.panel.service.PermissionService;
import com.omni.panel.service.RecentService;

class RecentServiceTest {
    private final RecentItemMapper mapper = mock(RecentItemMapper.class);
    private final ChartMapper chartMapper = mock(ChartMapper.class);
    private final DashboardMapper dashboardMapper = mock(DashboardMapper.class);
    private final DatasetMapper datasetMapper = mock(DatasetMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final RecentService service = new RecentService(
            mapper, chartMapper, dashboardMapper, datasetMapper, permissionService);

    @Test
    void touch委托持久化并补充资源名称() {
        RecentItemEntity item = new RecentItemEntity();
        item.setResourceType("QUESTION");
        item.setResourceId(8L);
        item.setVisitedAt(LocalDateTime.now());
        ChartEntity chart = new ChartEntity();
        chart.setId(8L);
        chart.setOwnerId(1L);
        chart.setName("销售趋势");
        chart.setDescription("按日");
        when(mapper.selectList(any())).thenReturn(List.of(item));
        when(chartMapper.selectById(8L)).thenReturn(chart);
        when(permissionService.canRead("CHART", 8L, 1L)).thenReturn(true);

        service.touch(1L, "QUESTION", 8L);
        List<RecentService.RecentView> views = service.list(1L, 20);

        verify(mapper).touch(1L, "QUESTION", 8L);
        assertThat(views).hasSize(1);
        assertThat(views.getFirst().resourceType()).isEqualTo("QUESTION");
        assertThat(views.getFirst().resourceId()).isEqualTo(8L);
        assertThat(views.getFirst().name()).isEqualTo("销售趋势");
    }

    @Test
    void 无读取权限的最近项不返回() {
        RecentItemEntity item = new RecentItemEntity();
        item.setResourceType("DASHBOARD");
        item.setResourceId(3L);
        item.setVisitedAt(LocalDateTime.now());
        DashboardEntity dashboard = new DashboardEntity();
        dashboard.setId(3L);
        dashboard.setOwnerId(9L);
        dashboard.setName("手机号段归属地分析");
        when(mapper.selectList(any())).thenReturn(List.of(item));
        when(dashboardMapper.selectById(3L)).thenReturn(dashboard);
        when(permissionService.canRead(eq("DASHBOARD"), eq(3L), eq(9L))).thenReturn(false);

        assertThat(service.list(2L, 20)).isEmpty();
    }
}
