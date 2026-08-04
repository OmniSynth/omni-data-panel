package com.omni.panel.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 启动时校验生产环境安全密钥，并在检测到默认管理员密码时执行安全初始化。
 */
@Component
public class SecurityBootstrap implements ApplicationRunner {
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final Set<String> WEAK_KEYS = Set.of(
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "");
    private final Environment environment;
    private final SecurityProperties properties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public SecurityBootstrap(Environment environment, SecurityProperties properties,
                             UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.environment = environment;
        this.properties = properties;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 执行生产环境安全引导；必要时使用配置的初始密码替换默认管理员密码。
     *
     * @param args 应用启动参数
     * @throws IllegalStateException 生产环境使用弱密钥，或默认管理员密码缺少合规替代值时抛出
     */
    @Override
    public void run(ApplicationArguments args) {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        String masterKey = environment.getProperty("omni.crypto.master-key", "");
        if (production && (WEAK_KEYS.contains(properties.jwtSecret()) || WEAK_KEYS.contains(masterKey))) {
            throw new IllegalStateException("生产环境必须配置独立的 JWT_SECRET 和 CREDENTIAL_MASTER_KEY");
        }
        SysUser admin = userMapper.selectOne(
            Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "admin"));
        if (!production || admin == null
            || !passwordEncoder.matches(DEFAULT_ADMIN_PASSWORD, admin.getPasswordHash())) {
            return;
        }
        String initialPassword = properties.initialAdminPassword();
        if (initialPassword == null || initialPassword.isBlank()
            || DEFAULT_ADMIN_PASSWORD.equals(initialPassword) || initialPassword.length() < 10) {
            throw new IllegalStateException(
                "生产环境检测到默认管理员密码，ADMIN_INITIAL_PASSWORD 必须配置为至少10位的非默认密码");
        }
        admin.setPasswordHash(passwordEncoder.encode(initialPassword));
        userMapper.updateById(admin);
    }
}
