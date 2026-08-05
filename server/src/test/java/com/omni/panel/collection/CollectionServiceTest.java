package com.omni.panel.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.CollectionEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.CollectionMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.DatasetMapper;
import com.omni.panel.mapper.MetricMapper;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.service.CollectionService;
import com.omni.panel.service.PermissionService;

class CollectionServiceTest {
    private final CollectionMapper collectionMapper = mock(CollectionMapper.class);
    private final ChartMapper chartMapper = mock(ChartMapper.class);
    private final DashboardMapper dashboardMapper = mock(DashboardMapper.class);
    private final DatasetMapper datasetMapper = mock(DatasetMapper.class);
    private final MetricMapper metricMapper = mock(MetricMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final CollectionService service = new CollectionService(
            collectionMapper, chartMapper, dashboardMapper, datasetMapper, metricMapper,
            mock(UserMapper.class), permissionService);

    @AfterEach
    void 清理() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 个人根集合已存在时直接返回() {
        authenticate(3L);
        CollectionEntity existing = new CollectionEntity();
        existing.setId(9L);
        existing.setPersonalOwnerId(3L);
        when(collectionMapper.selectOne(any())).thenReturn(existing);

        assertThat(service.ensurePersonalCollection(3L).getId()).isEqualTo(9L);
    }

    @Test
    void 删除个人根集合被拒绝() {
        authenticate(3L);
        CollectionEntity personal = new CollectionEntity();
        personal.setId(1L);
        personal.setOwnerId(3L);
        personal.setPersonalOwnerId(3L);
        personal.setArchived(false);
        when(collectionMapper.selectById(1L)).thenReturn(personal);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("个人根集合");
    }

    private void authenticate(long userId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "u", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
