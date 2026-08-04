package com.omni.panel.recent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.RecentItemEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.DatasetMapper;
import com.omni.panel.mapper.RecentItemMapper;
import com.omni.panel.service.RecentService;

class RecentServiceTest {
    @Test
    void touch委托持久化并补充资源名称() {
        RecentItemMapper mapper = mock(RecentItemMapper.class);
        ChartMapper chartMapper = mock(ChartMapper.class);
        DashboardMapper dashboardMapper = mock(DashboardMapper.class);
        DatasetMapper datasetMapper = mock(DatasetMapper.class);
        RecentService service = new RecentService(mapper, chartMapper, dashboardMapper, datasetMapper);
        RecentItemEntity item = new RecentItemEntity();
        item.setResourceType("QUESTION");
        item.setResourceId(8L);
        item.setVisitedAt(LocalDateTime.now());
        ChartEntity chart = new ChartEntity();
        chart.setId(8L);
        chart.setName("销售趋势");
        chart.setDescription("按日");
        when(mapper.selectList(any())).thenReturn(List.of(item));
        when(chartMapper.selectById(8L)).thenReturn(chart);

        service.touch(1L, "QUESTION", 8L);
        List<RecentService.RecentView> views = service.list(1L, 20);

        verify(mapper).touch(1L, "QUESTION", 8L);
        assertThat(views).hasSize(1);
        assertThat(views.getFirst().resourceType()).isEqualTo("QUESTION");
        assertThat(views.getFirst().resourceId()).isEqualTo(8L);
        assertThat(views.getFirst().name()).isEqualTo("销售趋势");
    }
}
