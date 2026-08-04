package com.omni.panel.datasource.dialect;

/**
 * 从 JDBC URL 解析出的连接端点。
 *
 * @param host            主机
 * @param port            端口
 * @param defaultDatabase 默认库/命名空间，可为 null
 */
public record ParsedJdbcUrl(String host, int port, String defaultDatabase) {
}
