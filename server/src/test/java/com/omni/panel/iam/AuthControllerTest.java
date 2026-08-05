package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.controller.AuthController;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.service.JwtService;
import com.omni.panel.service.LoginAuditService;
import com.omni.panel.service.UserService;

class AuthControllerTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder =
            mock(org.springframework.security.crypto.password.PasswordEncoder.class);
    private final AuthController controller = new AuthController(
            userMapper, passwordEncoder, mock(JwtService.class), mock(LoginAuditService.class),
            mock(UserService.class));

    @AfterEach
    void 清理认证上下文() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 修改密码时校验当前密码并更新BCrypt哈希() {
        SysUser user = 登录用户();
        when(userMapper.selectById(7L)).thenReturn(user);
        when(passwordEncoder.matches("旧密码123456", "旧哈希")).thenReturn(true);
        when(passwordEncoder.encode("新密码123456")).thenReturn("新哈希");

        var response = controller.changePassword(
                new AuthController.ChangePasswordRequest("旧密码123456", "新密码123456"));

        assertThat(response.code()).isZero();
        assertThat(user.getPasswordHash()).isEqualTo("新哈希");
        verify(userMapper).updateById(user);
    }

    @Test
    void 当前密码错误时拒绝更新() {
        SysUser user = 登录用户();
        when(userMapper.selectById(7L)).thenReturn(user);

        assertThatThrownBy(() -> controller.changePassword(
                new AuthController.ChangePasswordRequest("错误密码12345", "新密码123456")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前密码错误");
        verify(userMapper, never()).updateById(user);
    }

    @Test
    void 新密码与当前密码相同时拒绝更新() {
        SysUser user = 登录用户();
        when(userMapper.selectById(7L)).thenReturn(user);
        when(passwordEncoder.matches("相同密码12345", "旧哈希")).thenReturn(true);

        assertThatThrownBy(() -> controller.changePassword(
                new AuthController.ChangePasswordRequest("相同密码12345", "相同密码12345")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("新密码不能与当前密码相同");
        verify(userMapper, never()).updateById(user);
    }

    @Test
    void 新密码少于十位时请求校验失败() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(
                    new AuthController.ChangePasswordRequest("当前密码12345", "短密码123"));

            assertThat(violations).extracting(violation -> violation.getMessage())
                    .containsExactly("新密码至少需要10位");
        }
    }

    private SysUser 登录用户() {
        var current = new AuthenticatedUser(7L, "tester", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(current, null, List.of()));
        var user = new SysUser();
        user.setId(7L);
        user.setPasswordHash("旧哈希");
        return user;
    }
}
