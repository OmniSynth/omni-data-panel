package com.omni.panel.publiclink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.entity.PublicLinkEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.PublicLinkMapper;
import com.omni.panel.service.PermissionService;
import com.omni.panel.service.PublicLinkService;

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

        PublicLinkEntity link = service.create("DASHBOARD", 7L, null);

        assertThat(link.getToken()).isNotBlank();
        assertThat(link.getResourceType()).isEqualTo("DASHBOARD");
        assertThat(link.getExpiresAt()).isNull();
        verify(mapper).insert(any(PublicLinkEntity.class));
        verify(permissionService).require("DASHBOARD", 7L, 2L, "WRITE");
    }

    @Test
    void 创建时可设置有效天数() {
        AuthenticatedUser user = new AuthenticatedUser(2L, "u", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        DashboardEntity dashboard = new DashboardEntity();
        dashboard.setId(7L);
        dashboard.setOwnerId(2L);
        when(dashboardMapper.selectById(7L)).thenReturn(dashboard);

        PublicLinkEntity link = service.create("DASHBOARD", 7L, 7);

        assertThat(link.getExpiresAt()).isNotNull();
        assertThat(link.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
        assertThat(link.getExpiresAt()).isBefore(LocalDateTime.now().plusDays(8));
    }

    @Test
    void 无效有效期天数被拒绝() {
        AuthenticatedUser user = new AuthenticatedUser(2L, "u", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        DashboardEntity dashboard = new DashboardEntity();
        dashboard.setId(7L);
        dashboard.setOwnerId(2L);
        when(dashboardMapper.selectById(7L)).thenReturn(dashboard);

        assertThatThrownBy(() -> service.create("DASHBOARD", 7L, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("有效期");
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

    @Test
    void 过期令牌不可访问() {
        PublicLinkEntity link = new PublicLinkEntity();
        link.setEnabled(true);
        link.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectOne(any())).thenReturn(link);

        assertThatThrownBy(() -> service.getByToken("abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("公开链接");
    }
}
