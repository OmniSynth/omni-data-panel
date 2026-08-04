package com.omni.panel.datasource.dialect;

/**
 * 对外暴露的可运行方言摘要。
 *
 * @param code 方言编码
 * @param label 显示名称
 * @param defaultPort 默认端口
 */
public record DialectInfo(String code, String label, int defaultPort) {
    static DialectInfo from(DialectPlugin plugin) {
        return new DialectInfo(plugin.code(), plugin.label(), plugin.defaultPort());
    }
}
