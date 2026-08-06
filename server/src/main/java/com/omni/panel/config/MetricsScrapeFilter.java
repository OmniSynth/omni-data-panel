package com.omni.panel.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 保护 {@code /actuator/prometheus}：未配置令牌时 404；令牌不匹配时 401。
 */
@Component
public class MetricsScrapeFilter extends OncePerRequestFilter {
    public static final String HEADER_METRICS_TOKEN = "X-Metrics-Token";

    private final ObservabilityProperties properties;

    /**
     * @param properties 可观测性配置（指标开关与令牌）
     */
    public MetricsScrapeFilter(ObservabilityProperties properties) {
        this.properties = properties;
    }

    /**
     * 仅对 {@code /actuator/prometheus} 路径执行本过滤器。
     *
     * @param request HTTP 请求
     * @return 非 Prometheus 路径时为 true（跳过过滤）
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.equals("/actuator/prometheus");
    }

    /**
     * 校验指标抓取令牌；未启用或令牌不匹配时拒绝访问。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param chain    过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.metricsEnabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String provided = resolveToken(request);
        if (!constantTimeEquals(properties.metricsToken(), provided)) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 从 {@code X-Metrics-Token} 或 {@code Authorization: Bearer} 头解析令牌。
     *
     * @param request HTTP 请求
     * @return 令牌字符串；未提供时为 null
     */
    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_METRICS_TOKEN);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    /**
     * 以恒定时间比较两个 UTF-8 字符串，降低时序侧信道风险。
     *
     * @param expected 配置的期望令牌
     * @param actual   请求提供的令牌
     * @return 内容完全一致时为 true
     */
    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
