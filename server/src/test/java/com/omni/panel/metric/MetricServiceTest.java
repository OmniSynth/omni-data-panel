package com.omni.panel.metric;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.CollectionEntity;
import com.omni.panel.entity.DatasetEntity;
import com.omni.panel.entity.MetricEntity;
import com.omni.panel.mapper.MetricMapper;
import com.omni.panel.service.CollectionService;
import com.omni.panel.service.DatasetService;
import com.omni.panel.service.MetricService;
import com.omni.panel.service.PermissionService;

class MetricServiceTest {
    private final MetricMapper mapper = mock(MetricMapper.class);
    private final DatasetService datasetService = mock(DatasetService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final CollectionService collectionService = mock(CollectionService.class);
    private final MetricService service =
            new MetricService(mapper, datasetService, permissionService, collectionService);

    @AfterEach
    void 清理() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 创建指标要求模型可读并写入个人集合() {
        AuthenticatedUser user = new AuthenticatedUser(5L, "u", false, List.of("dataset:create"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        when(datasetService.require(2L, "READ")).thenReturn(new DatasetEntity());
        CollectionEntity personal = new CollectionEntity();
        personal.setId(11L);
        when(collectionService.ensurePersonalCollection(5L)).thenReturn(personal);

        service.create("销售额", null, 2L, "{}", "SUM", null);

        verify(mapper).insert(any(MetricEntity.class));
        verify(collectionService).ensurePersonalCollection(5L);
    }

    @Test
    void 非法聚合被拒绝() {
        AuthenticatedUser user = new AuthenticatedUser(5L, "u", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        when(datasetService.require(2L, "READ")).thenReturn(new DatasetEntity());

        assertThatThrownBy(() -> service.create("x", null, 2L, "{}", "MEDIAN", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("聚合");
    }
}
