package com.omni.panel.datasource.dialect;

/**
 * 对外暴露的可运行方言摘要。
 *
 * @param code        方言编码
 * @param label       显示名称
 * @param defaultPort 默认端口
 */
public record DialectInfo(String code, String label, int defaultPort) {
    /**
     * 从方言插件构造对外暴露的摘要信息。
     *
     * @param plugin 方言插件
     * @return 方言摘要
     */
    static DialectInfo from(DialectPlugin plugin) {
        return new DialectInfo(plugin.code(), plugin.label(), plugin.defaultPort());
    }
}
