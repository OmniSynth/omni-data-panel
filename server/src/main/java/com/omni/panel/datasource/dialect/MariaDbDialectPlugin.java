package com.omni.panel.datasource.dialect;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.omni.panel.common.BusinessException;

/**
 * MariaDB 方言插件：连接语义与 MySQL 一致，仅 JDBC URL 前缀不同。
 */
@Component
public class MariaDbDialectPlugin extends MysqlDialectPlugin {
    public static final String CODE = "MARIADB";

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)^jdbc:mariadb://([^/:?]+)(?::(\\d+))?(?:/([^?;\\s]*))?.*$");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "MariaDB";
    }

    @Override
    public boolean matchesJdbcUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mariadb:");
    }

    @Override
    public String buildJdbcUrl(String host, int port, String defaultDatabase) {
        String normalizedHost = JdbcConnectionFields.requireHost(host);
        JdbcConnectionFields.requirePort(port);
        StringBuilder url = new StringBuilder("jdbc:mariadb://")
                .append(normalizedHost)
                .append(':')
                .append(port);
        String database = JdbcConnectionFields.blankToNull(defaultDatabase);
        if (database != null) {
            url.append('/').append(database);
        }
        return url.toString();
    }

    @Override
    public ParsedJdbcUrl parseJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(jdbcUrl.trim());
        if (!matcher.matches()) {
            return null;
        }
        String host = matcher.group(1);
        int port = matcher.group(2) == null ? defaultPort() : Integer.parseInt(matcher.group(2));
        String database = JdbcConnectionFields.blankToNull(matcher.group(3));
        return new ParsedJdbcUrl(host, port, database);
    }

    @Override
    public void validateJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.matches("(?i)^jdbc:mariadb://[^;\\s]+$")) {
            throw new BusinessException("仅支持单个 MariaDB JDBC URL（可带或不带库名）");
        }
    }
}
