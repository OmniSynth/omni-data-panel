package com.omni.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业 OIDC SSO 配置；未启用或缺关键项时不注册 OAuth2 客户端。
 *
 * @param enabled            是否启用
 * @param issuerUri          IdP Issuer URI
 * @param clientId           客户端 ID
 * @param clientSecret       客户端密钥
 * @param clientName         登录按钮展示名
 * @param defaultRoleCode    JIT 建号默认角色编码
 * @param frontendRedirectUri 回调后前端兑换页完整 URL；空则由 frontend-url 推导
 */
@ConfigurationProperties("omni.security.oidc")
public record OidcProperties(
        Boolean enabled,
        String issuerUri,
        String clientId,
        String clientSecret,
        String clientName,
        String defaultRoleCode,
        String frontendRedirectUri) {

    public static final String REGISTRATION_ID = "omni";

    public OidcProperties {
        if (enabled == null) {
            enabled = false;
        }
        if (clientName == null || clientName.isBlank()) {
            clientName = "企业登录";
        }
        if (defaultRoleCode == null || defaultRoleCode.isBlank()) {
            defaultRoleCode = "USER";
        }
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /** 是否已配置到可启动 OAuth2 客户端的程度。 */
    public boolean isConfigured() {
        return isEnabled()
                && issuerUri != null && !issuerUri.isBlank()
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
