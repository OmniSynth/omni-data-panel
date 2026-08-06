package com.omni.panel.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import com.omni.panel.common.ClientRequestInfo;
import com.omni.panel.service.LoginAuditService;
import com.omni.panel.subscription.SubscriptionProperties;

/**
 * OIDC 登录失败时记录审计并重定向前端回调页。
 */
@Component
@ConditionalOnProperty(prefix = "omni.security.oidc", name = "enabled", havingValue = "true")
public class OidcLoginFailureHandler implements AuthenticationFailureHandler {
    private final LoginAuditService loginAuditService;
    private final OidcProperties oidcProperties;
    private final SubscriptionProperties subscriptionProperties;

    public OidcLoginFailureHandler(LoginAuditService loginAuditService, OidcProperties oidcProperties,
                                   SubscriptionProperties subscriptionProperties) {
        this.loginAuditService = loginAuditService;
        this.oidcProperties = oidcProperties;
        this.subscriptionProperties = subscriptionProperties;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String message = exception.getMessage() == null ? "OIDC 登录失败" : exception.getMessage();
        loginAuditService.record("-", null, false, "OIDC: " + message, ClientRequestInfo.from(request));
        String target = UriComponentsBuilder.fromUriString(resolveFrontendRedirect())
                .replaceQuery(null)
                .queryParam("error", message)
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
