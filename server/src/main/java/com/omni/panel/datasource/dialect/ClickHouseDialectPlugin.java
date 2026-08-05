package com.omni.panel.datasource.dialect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zaxxer.hikari.HikariConfig;
import org.springframework.stereotype.Component;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.DataSourceEntity;

/**
 * ClickHouse 方言插件。命名空间对应 database，与 MySQL 类似。
 */
@Component
public class ClickHouseDialectPlugin extends DialectPlugin {
    public static final String CODE = "CLICKHOUSE";

    private static final Set<String> SYSTEM_NAMESPACES = Set.of(
            "system", "information_schema", "INFORMATION_SCHEMA");

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)^jdbc:(?:clickhouse|ch)://([^/:?]+)(?::(\\d+))?(?:/([^?;\\s]*))?.*$");

    /** {@inheritDoc} */
    @Override
    public String code() {
        return CODE;
    }

    /** {@inheritDoc} */
    @Override
    public String label() {
        return "ClickHouse";
    }

    /** {@inheritDoc} */
    @Override
    public int defaultPort() {
        return 8123;
    }

    /** {@inheritDoc} */
    @Override
    public boolean matchesJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return false;
        }
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        return lower.startsWith("jdbc:clickhouse:") || lower.startsWith("jdbc:ch:");
    }

    /** {@inheritDoc} */
    @Override
    public String buildJdbcUrl(String host, int port, String defaultDatabase) {
        String normalizedHost = JdbcConnectionFields.requireHost(host);
        JdbcConnectionFields.requirePort(port);
        String database = JdbcConnectionFields.blankToNull(defaultDatabase);
        StringBuilder url = new StringBuilder("jdbc:clickhouse://")
                .append(normalizedHost)
                .append(':')
                .append(port);
        if (database != null) {
            if (database.contains("/") || database.contains("?") || database.contains(";")) {
                throw new BusinessException("数据库名不合法");
            }
            url.append('/').append(database);
        }
        return url.toString();
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void validateJdbcUrl(String jdbcUrl) {
        if (parseJdbcUrl(jdbcUrl) == null
                || jdbcUrl == null
                || !jdbcUrl.matches("(?i)^jdbc:(?:clickhouse|ch)://[^;\\s]+$")) {
            throw new BusinessException("仅支持单个 ClickHouse JDBC URL（可带或不带库名）");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void configurePool(HikariConfig config, DataSourceEntity source) {
        config.setConnectionInitSql("SET readonly=1");
        config.setConnectionTestQuery("SELECT 1");
    }

    /** {@inheritDoc} */
    @Override
    public void prepareConnection(Connection connection, DataSourceEntity source) throws SQLException {
        String database = source.getDefaultDatabase();
        if (database != null && !database.isBlank()) {
            connection.setCatalog(database.trim());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void restoreConnection(Connection connection, String preferredNamespace, String originalNamespace)
            throws SQLException {
        if (preferredNamespace != null && !preferredNamespace.isBlank()) {
            connection.setCatalog(preferredNamespace.trim());
            return;
        }
        if (originalNamespace != null && !originalNamespace.isBlank()) {
            connection.setCatalog(originalNamespace);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void useNamespace(Connection connection, String namespace) throws SQLException {
        connection.setCatalog(namespace);
    }

    /** {@inheritDoc} */
    @Override
    public List<String> listNamespaces(Connection connection, DatabaseMetaData metadata) throws SQLException {
        Set<String> catalogs = new LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getCatalogs()) {
            while (resultSet.next()) {
                addBusinessNamespace(catalogs, resultSet.getString(1));
            }
        }
        if (catalogs.isEmpty()) {
            collectFromSystemDatabases(connection, catalogs);
        }
        if (catalogs.isEmpty()) {
            addBusinessNamespace(catalogs, connection.getCatalog());
        }
        return List.copyOf(catalogs);
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> systemNamespaces() {
        return SYSTEM_NAMESPACES;
    }

    /** {@inheritDoc} */
    @Override
    public List<DialectTableInfo> listTablesFallback(Connection connection, String namespace)
            throws SQLException {
        String sql = """
                SELECT name, comment
                FROM system.tables
                WHERE database = ? AND is_temporary = 0
                ORDER BY name
                """;
        List<DialectTableInfo> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tables.add(new DialectTableInfo(resultSet.getString(1), resultSet.getString(2)));
                }
            }
        }
        return tables;
    }

    /** {@inheritDoc} */
    @Override
    public List<DialectColumnInfo> listColumnsFallback(Connection connection, String namespace, String table)
            throws SQLException {
        String sql = """
                SELECT name, type, position
                FROM system.columns
                WHERE database = ? AND table = ?
                ORDER BY position
                """;
        List<DialectColumnInfo> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(new DialectColumnInfo(
                            resultSet.getString("name"),
                            resultSet.getString("type"),
                            null,
                            null,
                            true,
                            resultSet.getInt("position"),
                            null
                    ));
                }
            }
        }
        return columns;
    }

    /** {@inheritDoc} */
    @Override
    public char identifierQuote() {
        return '`';
    }

    /**
     * 通过 system.databases 收集业务库名。
     *
     * @param connection JDBC 连接
     * @param catalogs   待填充的库名集合
     */
    private void collectFromSystemDatabases(Connection connection, Set<String> catalogs) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name FROM system.databases WHERE name NOT IN ('system','INFORMATION_SCHEMA','information_schema') ORDER BY name")) {
            while (resultSet.next()) {
                addBusinessNamespace(catalogs, resultSet.getString(1));
            }
        }
    }

    /**
     * 将非系统库名加入集合，忽略空值与 ClickHouse 系统库。
     *
     * @param catalogs 业务库名集合
     * @param catalog  待判定的库名
     */
    private void addBusinessNamespace(Set<String> catalogs, String catalog) {
        if (catalog == null || catalog.isBlank()) {
            return;
        }
        if (SYSTEM_NAMESPACES.contains(catalog) || SYSTEM_NAMESPACES.contains(catalog.toLowerCase(Locale.ROOT))) {
            return;
        }
        catalogs.add(catalog);
    }
}
