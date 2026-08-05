package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.SysRoleEntity;
import com.omni.panel.mapper.RoleMapper;
import com.omni.panel.service.RoleService;

class RoleServiceTest {
    @AfterEach
    void 清理认证上下文() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ADMIN内置角色不可编辑删除或配置权限() {
        RoleMapper mapper = mock(RoleMapper.class);
        RoleService service = new RoleService(mapper);
        SysRoleEntity admin = new SysRoleEntity();
        admin.setId(1L);
        admin.setCode("ADMIN");
        admin.setBuiltIn(true);
        admin.setEnabled(true);
        when(mapper.selectById(1L)).thenReturn(admin);
        AuthenticatedUser principal = new AuthenticatedUser(1L, "admin", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThatThrownBy(() -> service.update(1L, "管理员", null, false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ADMIN");
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ADMIN");
        assertThatThrownBy(() -> service.replacePermissions(1L, List.of()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ADMIN");
    }
}
