package com.omni.panel.publiclink;

import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.common.BusinessException;
import com.omni.panel.permission.PermissionService;
import com.omni.panel.visualization.ChartMapper;
import com.omni.panel.visualization.DashboardEntity;
import com.omni.panel.visualization.DashboardMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicLinkServiceTest {
    private final PublicLinkMapper mapper = mock(PublicLinkMapper.class);
    private final DashboardMapper dashboardMapper = mock(DashboardMapper.class);
    private final ChartMapper chartMapper = mock(ChartMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final PublicLinkService service =
        new PublicLinkService(mapper, dashboardMapper, chartMapper, permissionService);

    @AfterEach
    void 清理() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 创建仪表盘公开链接要求写权限() {
        AuthenticatedUser user = new AuthenticatedUser(2L, "u", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, List.of()));
        DashboardEntity dashboard = new DashboardEntity();
        dashboard.setId(7L);
        dashboard.setOwnerId(2L);
        when(dashboardMapper.selectById(7L)).thenReturn(dashboard);

        PublicLinkEntity link = service.create("DASHBOARD", 7L);

        assertThat(link.getToken()).isNotBlank();
        assertThat(link.getResourceType()).isEqualTo("DASHBOARD");
        verify(mapper).insert(any(PublicLinkEntity.class));
        verify(permissionService).require("DASHBOARD", 7L, 2L, "WRITE");
    }

    @Test
    void 禁用令牌不可访问() {
        PublicLinkEntity link = new PublicLinkEntity();
        link.setEnabled(false);
        when(mapper.selectOne(any())).thenReturn(link);

        assertThatThrownBy(() -> service.getByToken("abc"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("公开链接");
    }
}
