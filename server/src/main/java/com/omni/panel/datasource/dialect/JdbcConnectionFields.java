package com.omni.panel.datasource.dialect;

import com.omni.panel.common.BusinessException;

/**
 * 主机/端口等连接字段的通用校验。
 */
public final class JdbcConnectionFields {
    /**
     * 禁止实例化。
     */
    private JdbcConnectionFields() {
    }

    public static String requireHost(String host) {
        if (host == null || host.isBlank()) {
            throw new BusinessException("请填写主机地址");
        }
        String normalized = host.trim();
        if (normalized.contains("/") || normalized.contains(" ") || normalized.contains(";")) {
            throw new BusinessException("主机地址不合法");
        }
        return normalized;
    }

    public static int requirePort(Integer port) {
        if (port == null || port < 1 || port > 65535) {
            throw new BusinessException("端口须在 1–65535 之间");
        }
        return port;
    }

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
