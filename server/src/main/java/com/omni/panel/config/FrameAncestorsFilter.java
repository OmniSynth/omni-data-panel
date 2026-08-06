package com.omni.panel.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.omni.panel.service.SettingService;

/**
 * 为全部响应下发仅含 {@code frame-ancestors} 的 Content-Security-Policy，
 * 限制可嵌套本应用的父页面 Origin。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class FrameAncestorsFilter extends OncePerRequestFilter {
    public static final String HEADER = "Content-Security-Policy";

    private final SettingService settingService;

    /**
     * @param settingService 站点设置（读取嵌入域名白名单）
     */
    public FrameAncestorsFilter(SettingService settingService) {
        this.settingService = settingService;
    }

    /**
     * 为响应设置 frame-ancestors CSP 头并继续过滤器链。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param chain    过滤器链
     */
    /**
     * 写入 CSP 后继续过滤链；读取白名单失败时回落为仅 {@code 'self'}，避免拖垮全部请求。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String csp;
        try {
            csp = settingService.frameAncestorsCsp();
        } catch (RuntimeException ignored) {
            csp = "frame-ancestors 'self'";
        }
        response.setHeader(HEADER, csp);
        chain.doFilter(request, response);
    }
}
