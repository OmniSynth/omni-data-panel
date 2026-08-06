package com.omni.panel.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import com.omni.panel.common.ClientRequestInfo;
import com.omni.panel.entity.SysUser;
import com.omni.panel.service.JwtService;
import com.omni.panel.service.LoginAuditService;
import com.omni.panel.service.OidcExchangeCodeService;
import com.omni.panel.service.OidcUserProvisioningService;
import com.omni.panel.service.UserSessionRegistry;
import com.omni.panel.subscription.SubscriptionProperties;

/**
 * OIDC 登录成功：映射本地用户、签发 Omni JWT、以一次性兑换码重定向前端。
 */
@Component
@ConditionalOnProperty(prefix = "omni.security.oidc", name = "enabled", havingValue = "true")
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final OidcUserProvisioningService provisioningService;
    private final JwtService jwtService;
    private final UserSessionRegistry sessionRegistry;
    private final OidcExchangeCodeService exchangeCodeService;
    private final LoginAuditService loginAuditService;
    private final OidcProperties oidcProperties;
    private final SubscriptionProperties subscriptionProperties;

    public OidcLoginSuccessHandler(OidcUserProvisioningService provisioningService, JwtService jwtService,
                                   UserSessionRegistry sessionRegistry, OidcExchangeCodeService exchangeCodeService,
                                   LoginAuditService loginAuditService, OidcProperties oidcProperties,
                                   SubscriptionProperties subscriptionProperties) {
        this.provisioningService = provisioningService;
        this.jwtService = jwtService;
        this.sessionRegistry = sessionRegistry;
        this.exchangeCodeService = exchangeCodeService;
        this.loginAuditService = loginAuditService;
        this.oidcProperties = oidcProperties;
        this.subscriptionProperties = subscriptionProperties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        ClientRequestInfo.Info client = ClientRequestInfo.from(request);
        try {
            if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
                redirectFailure(response, "OIDC 主体无效");
                return;
            }
            SysUser user = provisioningService.provision(oidcUser);
            JwtService.AccessToken access = jwtService.createAccess(user.getId(), user.getUsername());
            sessionRegistry.register(user.getId(), access.jti(), access.expiresAt());
            String code = exchangeCodeService.issue(access.token());
            loginAuditService.record(user.getUsername(), user.getId(), true, "OIDC 登录成功", client);
            response.sendRedirect(frontendCallback(code));
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? "OIDC 登录失败" : exception.getMessage();
            loginAuditService.record("-", null, false, "OIDC: " + message, client);
            redirectFailure(response, message);
        }
    }

    private String frontendCallback(String code) {
        return UriComponentsBuilder.fromUriString(resolveFrontendRedirect())
                .replaceQuery(null)
                .queryParam("code", code)
                .encode()
                .build()
                .toUriString();
    }

    private void redirectFailure(HttpServletResponse response, String message) throws IOException {
        String target = UriComponentsBuilder.fromUriString(resolveFrontendRedirect())
                .replaceQuery(null)
                .queryParam("error", message == null ? "OIDC 登录失败" : message)
                .encode()
                .build()
                .toUriString();
        response.sendRedirect(target);
    }

    private String resolveFrontendRedirect() {
        String configured = oidcProperties.frontendRedirectUri();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String base = subscriptionProperties.getFrontendUrl();
        if (base == null || base.isBlank()) {
            base = "http://localhost:5173";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/login/oidc/callback";
    }
}
