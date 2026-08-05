package com.omni.panel.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全认证配置。
 *
 * @param jwtSecret            JWT 签名密钥的 Base64 编码
 * @param jwtExpiration        JWT 有效期，未配置时默认为 8 小时
 * @param initialAdminPassword 生产环境首次启动时用于替换默认管理员密码的初始密码
 */
@ConfigurationProperties("omni.security")
public record SecurityProperties(String jwtSecret, Duration jwtExpiration, String initialAdminPassword) {
    public SecurityProperties {
        if (jwtExpiration == null) {
            jwtExpiration = Duration.ofHours(8);
        }
    }
}
