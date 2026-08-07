package com.omni.panel.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求生成或透传 {@code X-Request-Id}，写入 MDC 与响应头，供日志关联。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,128}$");

    /**
     * 解析或生成 requestId，写入 MDC 与响应头，并在请求结束后清理 MDC。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param chain    过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 规范化入站 requestId；非法则生成新 UUID。
     *
     * @param inbound 入站头
     * @return 可用的 requestId
     */
    static String resolve(String inbound) {
        if (inbound == null || inbound.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        String trimmed = inbound.trim();
        if (SAFE_ID.matcher(trimmed).matches()) {
            return trimmed;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 当前 MDC 中的 requestId，可能为 null。
     */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
