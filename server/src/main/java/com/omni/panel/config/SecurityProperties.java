package com.omni.panel.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全认证与可信代理配置。
 *
 * @param jwtSecret            JWT 签名密钥的 Base64 编码
 * @param jwtExpiration        JWT 有效期，未配置时默认为 8 小时
 * @param initialAdminPassword 生产环境首次启动时用于替换默认管理员密码的初始密码
 * @param trustedProxies       可信反向代理 CIDR/IP 列表（逗号或空白分隔）；空则不信任转发头
 */
@ConfigurationProperties("omni.security")
public record SecurityProperties(String jwtSecret, Duration jwtExpiration, String initialAdminPassword,
                                 String trustedProxies) {
    public SecurityProperties {
        if (jwtExpiration == null) {
            jwtExpiration = Duration.ofHours(8);
        }
        if (trustedProxies == null) {
            trustedProxies = "";
        }
    }

    /**
     * 解析可信代理 CIDR / 精确 IP 列表。
     *
     * @return 非空条目列表（可能为空）
     */
    public List<String> trustedProxyEntries() {
        if (trustedProxies.isBlank()) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String part : trustedProxies.split("[,\\s]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }
}
