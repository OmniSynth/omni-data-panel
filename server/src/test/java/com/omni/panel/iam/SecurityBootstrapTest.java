package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.omni.panel.config.SecurityBootstrap;
import com.omni.panel.config.SecurityProperties;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.UserMapper;

class SecurityBootstrapTest {
    private static final String FLYWAY_DEFAULT_HASH =
            "$2a$10$4ruqE8FlnERNCuIW/6pI6.1rlZmJiG/plwFwif5KPGxjwbM9Sm6je";
    private static final String JWT_SECRET = "独立的生产JWT密钥";
    private static final String MASTER_KEY = "独立的生产主密钥";
    private final Environment environment = mock(Environment.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void 生产环境默认密码缺少合格初始密码时拒绝启动() {
        SysUser admin = adminWithHash(FLYWAY_DEFAULT_HASH);
        配置生产环境(admin);
        var bootstrap = bootstrap("");

        assertThatThrownBy(() -> bootstrap.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_INITIAL_PASSWORD");
        verify(userMapper, never()).updateById(admin);
    }

    @Test
    void 生产环境仅在管理员仍使用默认密码时替换() {
        SysUser admin = adminWithHash(FLYWAY_DEFAULT_HASH);
        配置生产环境(admin);
        var bootstrap = bootstrap("production_admin_2026");

        bootstrap.run(null);

        assertThat(passwordEncoder.matches("production_admin_2026", admin.getPasswordHash())).isTrue();
        verify(userMapper).updateById(admin);
    }

    @Test
    void 用户已修改密码时不再重置() {
        SysUser admin = adminWithPassword("用户自定义密码123");
        配置生产环境(admin);
        var bootstrap = bootstrap("production_admin_2026");

        bootstrap.run(null);

        assertThat(passwordEncoder.matches("用户自定义密码123", admin.getPasswordHash())).isTrue();
        verify(userMapper, never()).updateById(admin);
    }

    private SecurityBootstrap bootstrap(String initialPassword) {
        var properties = new SecurityProperties(JWT_SECRET, Duration.ofHours(8), initialPassword, null);
        return new SecurityBootstrap(environment, properties, userMapper, passwordEncoder);
    }

    private void 配置生产环境(SysUser admin) {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(environment.getProperty("omni.crypto.master-key", "")).thenReturn(MASTER_KEY);
        when(userMapper.selectOne(any())).thenReturn(admin);
    }

    private SysUser adminWithPassword(String password) {
        return adminWithHash(passwordEncoder.encode(password));
    }

    private SysUser adminWithHash(String passwordHash) {
        var admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash(passwordHash);
        return admin;
    }
}
