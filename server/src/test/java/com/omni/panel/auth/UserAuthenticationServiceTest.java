package com.omni.panel.auth;

import com.omni.panel.common.BusinessException;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAuthenticationServiceTest {
    private final UserMapper mapper = mock(UserMapper.class);
    private final UserAuthenticationService service = new UserAuthenticationService(mapper);

    @Test
    void 多角色功能权限取并集并生成全部角色权威() {
        SysUser user = user(true);
        when(mapper.selectById(7L)).thenReturn(user);
        when(mapper.findRoles(7L)).thenReturn(List.of("REPORT_VIEWER", "EXPORTER"));
        when(mapper.findPermissions(7L)).thenReturn(List.of("query:execute", "export:execute"));

        var authentication = service.load(7L);
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();

        assertThat(principal.roleCodes()).containsExactly("REPORT_VIEWER", "EXPORTER");
        assertThat(principal.permissions()).containsExactly("query:execute", "export:execute");
        assertThat(authentication.getAuthorities()).extracting(Object::toString)
            .contains("ROLE_REPORT_VIEWER", "ROLE_EXPORTER", "query:execute", "export:execute");
    }

    @Test
    void 禁用用户在下一次认证加载时立即失效() {
        when(mapper.selectById(7L)).thenReturn(user(false));

        assertThatThrownBy(() -> service.load(7L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已禁用");
    }

    @Test
    void 角色和功能权限查询仅包含启用角色() throws Exception {
        String rolesSql = String.join(" ", UserMapper.class.getMethod("findRoles", long.class)
            .getAnnotation(Select.class).value());
        String permissionsSql = String.join(" ", UserMapper.class.getMethod("findPermissions", long.class)
            .getAnnotation(Select.class).value());

        assertThat(rolesSql).contains("r.enabled = TRUE");
        assertThat(permissionsSql).contains("r.enabled = TRUE", "SELECT DISTINCT p.code");
    }

    private SysUser user(boolean enabled) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("tester");
        user.setEnabled(enabled);
        return user;
    }
}
