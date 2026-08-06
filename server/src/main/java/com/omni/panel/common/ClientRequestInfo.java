package com.omni.panel.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从 HTTP 请求提取客户端 IP 与 User-Agent。
 * <p>IP 解析委托 {@link ClientIpResolver}（可信代理配置）；解析器未注册时仅使用 {@code remoteAddr}。
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
        ClientIpResolver resolver = ClientIpResolver.current();
        String ip = resolver != null
                ? resolver.resolveClientIp(request)
                : truncate(blankToNull(request.getRemoteAddr()), 64);
        return new Info(ip, truncate(request.getHeader("User-Agent"), 512));
    }

    /**
     * 空白字符串转为 null，非空白则 trim。
     *
     * @param value 原始字符串
     * @return trim 后的值；null 或空白时为 null
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
     * @return trim 后的值；超长时截取前 max 个字符；null 时为 null
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
