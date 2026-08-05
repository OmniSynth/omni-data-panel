package com.omni.panel.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从 HTTP 请求提取客户端 IP 与 User-Agent。
 */
public final class ClientRequestInfo {
    /**
     * 工具类，禁止实例化。
     */
    private ClientRequestInfo() {
    }

    /**
     * 从 HTTP 请求提取客户端 IP 与 User-Agent。
     *
     * @param request 当前 HTTP 请求，可为 null
     * @return 客户端信息；request 为空时字段均为 null
     */
    public static Info from(HttpServletRequest request) {
        if (request == null) {
            return new Info(null, null);
        }
        return new Info(resolveIp(request), truncate(request.getHeader("User-Agent"), 512));
    }

    /**
     * 从请求头或直连地址解析客户端 IP。
     *
     * @param request HTTP 请求
     * @return 截断后的 IP 字符串，无法解析时为 null
     */
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

    /**
     * 从 {@code X-Forwarded-For} 头提取首个客户端 IP。
     *
     * @param forwardedFor 转发链 IP 头
     * @return 首个 IP，头为空或无效时为 null
     */
    private static String firstIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        String first = forwardedFor.split(",")[0].trim();
        return first.isEmpty() ? null : truncate(first, 64);
    }

    /**
     * 将空白字符串规范化为 null。
     *
     * @param value 原始字符串
     * @return 去首尾空白后的非空字符串，或 null
     */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 截断字符串至指定最大长度。
     *
     * @param value 原始字符串
     * @param max   最大字符数
     * @return 截断后的字符串，输入为 null 时返回 null
     */
    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /**
     * @param clientIp  客户端 IP
     * @param userAgent 浏览器 User-Agent
     */
    public record Info(String clientIp, String userAgent) {
    }
}
