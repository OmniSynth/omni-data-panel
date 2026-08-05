package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.mapper.ResourceOwnerMapper;
import com.omni.panel.mapper.ResourcePermissionMapper;
import com.omni.panel.service.PermissionService;

class PermissionServiceTest {
    @AfterEach
    void 清理上下文() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 多个启用角色按写高于读计算资源权限() throws Exception {
        String sql = String.join(" ", ResourcePermissionMapper.class
                .getMethod("hasPermission", String.class, long.class, long.class, String.class)
                .getAnnotation(Select.class).value());
        String accessSql = String.join(" ", ResourcePermissionMapper.class
                .getMethod("permission", String.class, long.class, long.class)
                .getAnnotation(Select.class).value());

        assertThat(sql).contains("r.enabled = TRUE", "#{permission} = 'READ' AND rp.permission = 'WRITE'");
        assertThat(accessSql).contains("MAX(CASE rp.permission", "WHEN 'WRITE' THEN 2");

        ResourcePermissionMapper mapper = mock(ResourcePermissionMapper.class);
        when(mapper.permission("DATA_SOURCE", 1, 7)).thenReturn("READ");
        PermissionService service = new PermissionService(mapper, mock(ResourceOwnerMapper.class));
        AuthenticatedUser user = new AuthenticatedUser(7, "tester", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        assertThat(service.canRead("DATA_SOURCE", 1, 9)).isTrue();
    }

    @Test
    void 数据源拒绝写授权而仪表盘允许读写授权() {
        ResourcePermissionMapper mapper = mock(ResourcePermissionMapper.class);
        ResourceOwnerMapper ownerMapper = mock(ResourceOwnerMapper.class);
        when(ownerMapper.dataSourceOwner(1)).thenReturn(9L);
        when(ownerMapper.dashboardOwner(2)).thenReturn(9L);
        when(mapper.assignableRole(5)).thenReturn(1);
        PermissionService service = new PermissionService(mapper, ownerMapper);
        AuthenticatedUser admin = new AuthenticatedUser(1, "admin", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        assertThatThrownBy(() -> service.grant("DATA_SOURCE", 1, 5, "WRITE"))
                .hasMessage("数据源仅允许授予 READ");
        service.grant("DASHBOARD", 2, 5, "READ");
        service.grant("DASHBOARD", 2, 5, "WRITE");

        verify(mapper).grant("DASHBOARD", 2, 5, "READ");
        verify(mapper).grant("DASHBOARD", 2, 5, "WRITE");
    }

    @Test
    void 资源所有者可以配置角色授权() {
        ResourcePermissionMapper mapper = mock(ResourcePermissionMapper.class);
        ResourceOwnerMapper ownerMapper = mock(ResourceOwnerMapper.class);
        when(ownerMapper.dashboardOwner(2)).thenReturn(9L);
        when(mapper.assignableRole(5)).thenReturn(1);
        PermissionService service = new PermissionService(mapper, ownerMapper);
        AuthenticatedUser owner = new AuthenticatedUser(9, "owner", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner, null, List.of()));

        service.grant("DASHBOARD", 2, 5, "READ");
        verify(mapper).grant("DASHBOARD", 2, 5, "READ");
    }

    @Test
    void 非所有者非管理员不能配置角色授权() {
        ResourcePermissionMapper mapper = mock(ResourcePermissionMapper.class);
        ResourceOwnerMapper ownerMapper = mock(ResourceOwnerMapper.class);
        when(ownerMapper.dashboardOwner(2)).thenReturn(9L);
        PermissionService service = new PermissionService(mapper, ownerMapper);
        AuthenticatedUser other = new AuthenticatedUser(3, "other", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(other, null, List.of()));

        assertThatThrownBy(() -> service.grant("DASHBOARD", 2, 5, "READ"))
                .isInstanceOf(com.omni.panel.common.BusinessException.class)
                .hasMessageContaining("管理员或资源所有者");
    }

    @Test
    void 个人集合禁止角色共享() {
        ResourcePermissionMapper mapper = mock(ResourcePermissionMapper.class);
        ResourceOwnerMapper ownerMapper = mock(ResourceOwnerMapper.class);
        when(ownerMapper.collectionOwner(10)).thenReturn(9L);
        when(ownerMapper.collectionPersonalOwner(10)).thenReturn(9L);
        PermissionService service = new PermissionService(mapper, ownerMapper);
        AuthenticatedUser owner = new AuthenticatedUser(9, "owner", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner, null, List.of()));

        assertThatThrownBy(() -> service.grant("COLLECTION", 10, 5, "READ"))
                .hasMessageContaining("个人集合");
        verify(mapper, never()).grant("COLLECTION", 10, 5, "READ");
    }

    @Test
    void 图表可读权限继承自集合祖先() {
        ResourcePermissionMapper mapper = mock(ResourcePermissionMapper.class);
        ResourceOwnerMapper ownerMapper = mock(ResourceOwnerMapper.class);
        when(mapper.permission("CHART", 100, 7)).thenReturn(null);
        when(ownerMapper.chartCollectionId(100)).thenReturn(20L);
        when(ownerMapper.collectionParentId(20)).thenReturn(10L);
        when(ownerMapper.collectionParentId(10)).thenReturn(null);
        when(mapper.permission("COLLECTION", 20, 7)).thenReturn(null);
        when(mapper.permission("COLLECTION", 10, 7)).thenReturn("READ");
        PermissionService service = new PermissionService(mapper, ownerMapper);
        AuthenticatedUser user = new AuthenticatedUser(7, "tester", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        assertThat(service.canRead("CHART", 100, 9)).isTrue();
        service.require("CHART", 100, 9, "READ");
        assertThatThrownBy(() -> service.require("CHART", 100, 9, "WRITE"))
                .hasMessageContaining("无权访问");
    }

    @Test
    void 资源直授写权限高于集合读权限() {
        ResourcePermissionMapper mapper = mock(ResourcePermissionMapper.class);
        ResourceOwnerMapper ownerMapper = mock(ResourceOwnerMapper.class);
        when(ownerMapper.chartOwner(100)).thenReturn(9L);
        when(mapper.permission("CHART", 100, 7)).thenReturn("WRITE");
        when(ownerMapper.chartCollectionId(100)).thenReturn(20L);
        when(mapper.permission("COLLECTION", 20, 7)).thenReturn("READ");
        PermissionService service = new PermissionService(mapper, ownerMapper);
        AuthenticatedUser user = new AuthenticatedUser(7, "tester", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        service.require("CHART", 100, 9, "WRITE");
        assertThat(service.accessLevel("CHART", 100)).isEqualTo("WRITE");
    }
}
