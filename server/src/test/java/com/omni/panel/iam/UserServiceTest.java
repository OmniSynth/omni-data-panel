package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.service.UserService;

class UserServiceTest {
    @AfterEach
    void 清理认证上下文() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 用户更新在事务中整体替换多个角色() throws Exception {
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserService service = new UserService(mapper, encoder);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("tester");
        when(mapper.selectById(7L)).thenReturn(user);
        when(mapper.countAssignableRoles(List.of(2L, 3L))).thenReturn(2);
        when(mapper.insertRoles(7L, List.of(2L, 3L))).thenReturn(2);
        when(mapper.findRoleIds(7L)).thenReturn(List.of(2L, 3L));
        when(mapper.findRoles(7L)).thenReturn(List.of("REPORT_VIEWER", "EXPORTER"));
        when(mapper.findPermissions(7L)).thenReturn(List.of("query:execute", "export:execute"));
        AuthenticatedUser admin = new AuthenticatedUser(1L, "admin", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        var result = service.update(7L, "测试用户", true, List.of(2L, 3L, 2L));

        var ordered = inOrder(mapper);
        ordered.verify(mapper).deleteRoles(7L);
        ordered.verify(mapper).insertRoles(7L, List.of(2L, 3L));
        assertThat(result.permissions()).containsExactly("query:execute", "export:execute");
        assertThat(UserService.class.getMethod(
                        "update", long.class, String.class, boolean.class, List.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }
}
