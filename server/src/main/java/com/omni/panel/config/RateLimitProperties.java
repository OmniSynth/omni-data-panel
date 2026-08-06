package com.omni.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录与公开接口 IP 限流配置（进程内固定窗口）。
 *
 * @param enabled         是否启用限流
 * @param authPerMinute   认证相关接口每 IP 每分钟限额
 * @param publicPerMinute 公开链接接口每 IP 每分钟限额
 * @param embedPerMinute  嵌入接口每 IP 每分钟限额
 */
@ConfigurationProperties("omni.security.rate-limit")
public record RateLimitProperties(Boolean enabled, Integer authPerMinute, Integer publicPerMinute,
                                  Integer embedPerMinute) {
    public RateLimitProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (authPerMinute == null || authPerMinute < 1) {
            authPerMinute = 30;
        }
        if (publicPerMinute == null || publicPerMinute < 1) {
            publicPerMinute = 120;
        }
        if (embedPerMinute == null || embedPerMinute < 1) {
            embedPerMinute = 180;
        }
    }

    /** 是否启用限流（缺省视为开启）。 */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
