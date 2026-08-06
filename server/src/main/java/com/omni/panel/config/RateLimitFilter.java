package com.omni.panel.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.ClientRequestInfo;

/**
 * 对登录与公开/嵌入 API 按客户端 IP 做固定窗口限流（Redis 优先）。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/login-challenge",
            "/api/auth/mfa/verify",
            "/api/auth/setup-password",
            "/api/auth/oidc/status",
            "/api/auth/oidc/exchange");

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final RateLimiter limiter;
    private final OmniMetrics omniMetrics;

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper, OmniMetrics omniMetrics,
                           RateLimiter limiter) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.omniMetrics = omniMetrics;
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        String bucket = resolveBucket(request.getRequestURI());
        if (bucket == null) {
            chain.doFilter(request, response);
            return;
        }
        String ip = ClientRequestInfo.from(request).clientIp();
        if (ip == null || ip.isBlank()) {
            ip = "unknown";
        }
        int limit = limitFor(bucket);
        if (!limiter.tryAcquire(bucket + ':' + ip, limit)) {
            omniMetrics.rateLimited(bucket);
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiResponse.error(429, "请求过于频繁，请稍后再试"));
            return;
        }
        chain.doFilter(request, response);
    }

    private String resolveBucket(String uri) {
        if (uri == null) {
            return null;
        }
        if (AUTH_PATHS.contains(uri)
                || uri.startsWith("/oauth2/authorization/")
                || uri.startsWith("/login/oauth2/code/")) {
            return "auth";
        }
        if (uri.startsWith("/api/public/") || "/api/public".equals(uri)) {
            return "public";
        }
        if (uri.startsWith("/api/embed/") || "/api/embed".equals(uri)) {
            return "embed";
        }
        return null;
    }

    private int limitFor(String bucket) {
        return switch (bucket) {
            case "auth" -> properties.authPerMinute();
            case "public" -> properties.publicPerMinute();
            case "embed" -> properties.embedPerMinute();
            default -> Integer.MAX_VALUE;
        };
    }
}
