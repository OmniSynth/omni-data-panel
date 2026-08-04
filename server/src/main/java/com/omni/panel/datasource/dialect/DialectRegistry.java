package com.omni.panel.datasource.dialect;

import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.DataSourceEntity;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 已注册的分析数据源方言插件表。
 */
@Component
public class DialectRegistry {
    private final Map<String, DialectPlugin> plugins;

    public DialectRegistry(List<DialectPlugin> plugins) {
        Map<String, DialectPlugin> map = new LinkedHashMap<>();
        for (DialectPlugin plugin : plugins) {
            // 跳过 MyBatis 误将 DialectPlugin 接口注册为 Mapper 后注入的 JDK 代理
            if (!isConcretePlugin(plugin)) {
                continue;
            }
            String code = plugin.code().trim().toUpperCase(Locale.ROOT);
            if (map.containsKey(code)) {
                throw new IllegalStateException("重复的方言插件编码：" + code);
            }
            map.put(code, plugin);
        }
        if (map.isEmpty()) {
            throw new IllegalStateException("未注册任何数据源方言插件");
        }
        this.plugins = Map.copyOf(map);
    }

    private static boolean isConcretePlugin(DialectPlugin plugin) {
        Class<?> type = AopUtils.getTargetClass(plugin);
        return !Proxy.isProxyClass(plugin.getClass())
            && !Proxy.isProxyClass(type)
            && !type.equals(DialectPlugin.class)
            && !type.isInterface();
    }

    /**
     * 可运行方言列表（稳定排序）。
     */
    public List<DialectInfo> list() {
        return plugins.values().stream()
            .sorted(Comparator.comparing(DialectPlugin::code))
            .map(DialectInfo::from)
            .toList();
    }

    /**
     * 按编码解析插件；空编码默认 MYSQL。
     */
    public DialectPlugin require(String code) {
        String normalized = normalize(code);
        DialectPlugin plugin = plugins.get(normalized);
        if (plugin == null) {
            throw new BusinessException("不支持的数据源方言：" + code);
        }
        return plugin;
    }

    /**
     * 规范化方言编码；空值默认 {@link MysqlDialectPlugin#CODE}。
     */
    public String normalize(String code) {
        if (code == null || code.isBlank()) {
            return MysqlDialectPlugin.CODE;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!plugins.containsKey(normalized)) {
            throw new BusinessException("不支持的数据源方言：" + code);
        }
        return normalized;
    }

    /**
     * 根据数据源配置解析方言插件。
     */
    public DialectPlugin resolve(DataSourceEntity source) {
        if (source == null) {
            return require(MysqlDialectPlugin.CODE);
        }
        if (source.getDialect() != null && !source.getDialect().isBlank()) {
            return require(source.getDialect());
        }
        return detect(source.getJdbcUrl());
    }

    /**
     * 根据 JDBC URL 推断方言；无法识别时抛错。
     */
    public DialectPlugin detect(String jdbcUrl) {
        if (jdbcUrl != null) {
            for (DialectPlugin plugin : plugins.values()) {
                if (plugin.matchesJdbcUrl(jdbcUrl)) {
                    return plugin;
                }
            }
        }
        throw new BusinessException("无法识别 JDBC URL 对应的数据源方言");
    }

    /**
     * 尝试用任一插件解析 URL。
     */
    public ParsedJdbcUrl parseAny(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        for (DialectPlugin plugin : plugins.values()) {
            ParsedJdbcUrl parsed = plugin.parseJdbcUrl(jdbcUrl);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    /**
     * 推断方言编码；无法识别时返回 MYSQL（兼容旧数据回填展示）。
     */
    public String detectCodeOrMysql(String jdbcUrl) {
        if (jdbcUrl != null) {
            for (DialectPlugin plugin : plugins.values()) {
                if (plugin.matchesJdbcUrl(jdbcUrl)) {
                    return plugin.code();
                }
            }
        }
        return MysqlDialectPlugin.CODE;
    }
}
