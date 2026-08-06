package com.omni.panel.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.config.OidcProperties;
import com.omni.panel.service.OidcExchangeCodeService;

/**
 * OIDC SSO 状态查询与一次性兑换码换取访问令牌。
 */
@RestController
@RequestMapping("/api/auth/oidc")
public class OidcAuthController {
    private final OidcProperties oidcProperties;
    private final OidcExchangeCodeService exchangeCodeService;

    public OidcAuthController(OidcProperties oidcProperties, OidcExchangeCodeService exchangeCodeService) {
        this.oidcProperties = oidcProperties;
        this.exchangeCodeService = exchangeCodeService;
    }

    /**
     * 查询 SSO 是否可用及授权入口。
     *
     * @return 状态
     */
    @GetMapping("/status")
    public ApiResponse<OidcStatus> status() {
        boolean enabled = oidcProperties.isConfigured();
        return ApiResponse.ok(new OidcStatus(
                enabled,
                enabled ? "/oauth2/authorization/" + OidcProperties.REGISTRATION_ID : null,
                oidcProperties.clientName()));
    }

    /**
     * 用一次性兑换码换取 Omni 访问令牌。
     *
     * @param request 兑换请求
     * @return 与本地登录一致的 Bearer 结果
     */
    @PostMapping("/exchange")
    public ApiResponse<AuthController.LoginResult> exchange(@Valid @RequestBody ExchangeRequest request) {
        String accessToken = exchangeCodeService.consume(request.code());
        return ApiResponse.ok(AuthController.LoginResult.bearer(accessToken));
    }

    /**
     * @param enabled          是否启用且已配置
     * @param authorizationUrl 浏览器跳转的授权入口（同源相对路径）
     * @param clientName       按钮文案
     */
    public record OidcStatus(boolean enabled, String authorizationUrl, String clientName) {
    }

    /**
     * @param code 一次性兑换码
     */
    public record ExchangeRequest(@NotBlank String code) {
    }
}
