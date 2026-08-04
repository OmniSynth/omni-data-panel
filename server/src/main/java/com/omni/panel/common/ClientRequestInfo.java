package com.omni.panel.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从 HTTP 请求提取客户端 IP 与 User-Agent。
 */
public final class ClientRequestInfo {
    private ClientRequestInfo() {}

    /**
     * @param request 当前 HTTP 请求，可为 null
     * @return 客户端信息；request 为空时字段均为 null
     */
    public static Info from(HttpServletRequest request) {
        if (request == null) {
            return new Info(null, null);
        }
        return new Info(resolveIp(request), truncate(request.getHeader("User-Agent"), 512));
    }

    private static String resolveIp(HttpServletRequest request) {
        String forwarded = firstIp(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            return forwarded;
        }
        String realIp = blankToNull(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return truncate(realIp, 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String firstIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        String first = forwardedFor.split(",")[0].trim();
        return first.isEmpty() ? null : truncate(first, 64);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /**
     * @param clientIp 客户端 IP
     * @param userAgent 浏览器 User-Agent
     */
    public record Info(String clientIp, String userAgent) {}
}
