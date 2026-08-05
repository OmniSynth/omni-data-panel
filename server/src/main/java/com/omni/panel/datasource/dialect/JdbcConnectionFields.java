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

    /**
     * 校验主机地址非空且不含非法字符。
     *
     * @param host 主机地址
     * @return 去首尾空白后的主机
     * @throws BusinessException 为空或格式非法时抛出
     */
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

    /**
     * 校验端口号在合法范围内。
     *
     * @param port 端口号
     * @return 有效端口号
     * @throws BusinessException 为空或超出 1–65535 时抛出
     */
    public static int requirePort(Integer port) {
        if (port == null || port < 1 || port > 65535) {
            throw new BusinessException("端口须在 1–65535 之间");
        }
        return port;
    }

    /**
     * 将空白字符串规范化为 null。
     *
     * @param value 原始字符串
     * @return 非空 trim 后的值，或 null
     */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
