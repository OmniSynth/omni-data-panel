package com.omni.panel.datasource.dialect;

import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.DataSourceEntity;
import com.zaxxer.hikari.HikariConfig;
import org.springframework.stereotype.Component;

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

/**
 * MySQL 分析数据源方言插件。
 */
@Component
public class MysqlDialectPlugin extends DialectPlugin {
    public static final String CODE = "MYSQL";

    private static final Set<String> SYSTEM_NAMESPACES = Set.of(
        "information_schema", "mysql", "performance_schema", "sys");

    private static final Pattern URL_PATTERN = Pattern.compile(
        "(?i)^jdbc:mysql://([^/:?]+)(?::(\\d+))?(?:/([^?;\\s]*))?.*$");

    private static final List<Pattern> FORBIDDEN = List.of(
        Pattern.compile("(?s).*\\block\\s+in\\s+share\\s+mode\\b.*"));

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "MySQL";
    }

    @Override
    public int defaultPort() {
        return 3306;
    }

    @Override
    public boolean matchesJdbcUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql:");
    }

    @Override
    public String buildJdbcUrl(String host, int port, String defaultDatabase) {
        String normalizedHost = JdbcConnectionFields.requireHost(host);
        JdbcConnectionFields.requirePort(port);
        StringBuilder url = new StringBuilder("jdbc:mysql://")
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
        if (jdbcUrl == null || !jdbcUrl.matches("(?i)^jdbc:mysql://[^;\\s]+$")) {
            throw new BusinessException("仅支持单个 MySQL JDBC URL（可带或不带库名）");
        }
    }

    @Override
    public void configurePool(HikariConfig config, DataSourceEntity source) {
        config.setConnectionInitSql("SET SESSION TRANSACTION READ ONLY");
    }

    @Override
    public void prepareConnection(Connection connection, DataSourceEntity source) throws SQLException {
        String defaultDatabase = source.getDefaultDatabase();
        if (defaultDatabase != null && !defaultDatabase.isBlank()) {
            connection.setCatalog(defaultDatabase.trim());
        }
    }

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

    @Override
    public void useNamespace(Connection connection, String namespace) throws SQLException {
        connection.setCatalog(namespace);
    }

    @Override
    public List<String> listNamespaces(Connection connection, DatabaseMetaData metadata) throws SQLException {
        Set<String> catalogs = new LinkedHashSet<>();
        collectFromMetaData(metadata, catalogs);
        if (catalogs.isEmpty()) {
            collectFromShowDatabases(connection, catalogs);
        }
        if (catalogs.isEmpty()) {
            collectFromInformationSchema(connection, catalogs);
        }
        if (catalogs.isEmpty()) {
            String fallback = connection.getCatalog();
            addBusinessNamespace(catalogs, fallback);
        }
        return List.copyOf(catalogs);
    }

    @Override
    public Set<String> systemNamespaces() {
        return SYSTEM_NAMESPACES;
    }

    @Override
    public List<DialectTableInfo> listTablesFallback(Connection connection, String namespace)
        throws SQLException {
        String sql = """
            SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = ? AND TABLE_TYPE IN ('BASE TABLE', 'VIEW')
            ORDER BY TABLE_NAME
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

    @Override
    public List<DialectColumnInfo> listColumnsFallback(Connection connection, String namespace, String table)
        throws SQLException {
        String sql = """
            SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION,
                   NUMERIC_SCALE, IS_NULLABLE, ORDINAL_POSITION, COLUMN_COMMENT
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """;
        List<DialectColumnInfo> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Long charLen = (Long) resultSet.getObject("CHARACTER_MAXIMUM_LENGTH");
                    Long numPrec = (Long) resultSet.getObject("NUMERIC_PRECISION");
                    Long numScale = (Long) resultSet.getObject("NUMERIC_SCALE");
                    Integer columnSize = charLen != null ? charLen.intValue()
                        : numPrec != null ? numPrec.intValue() : null;
                    Integer decimalDigits = numScale == null ? null : numScale.intValue();
                    columns.add(new DialectColumnInfo(
                        resultSet.getString("COLUMN_NAME"),
                        resultSet.getString("COLUMN_TYPE"),
                        columnSize,
                        decimalDigits,
                        "YES".equalsIgnoreCase(resultSet.getString("IS_NULLABLE")),
                        resultSet.getInt("ORDINAL_POSITION"),
                        resultSet.getString("COLUMN_COMMENT")
                    ));
                }
            }
        }
        return columns;
    }

    @Override
    public char identifierQuote() {
        return '`';
    }

    @Override
    public List<Pattern> forbiddenSqlPatterns() {
        return FORBIDDEN;
    }

    private void collectFromMetaData(DatabaseMetaData metadata, Set<String> catalogs) throws SQLException {
        try (ResultSet resultSet = metadata.getCatalogs()) {
            while (resultSet.next()) {
                addBusinessNamespace(catalogs, resultSet.getString(1));
            }
        }
    }

    private void collectFromShowDatabases(Connection connection, Set<String> catalogs) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW DATABASES")) {
            while (resultSet.next()) {
                addBusinessNamespace(catalogs, resultSet.getString(1));
            }
        }
    }

    private void collectFromInformationSchema(Connection connection, Set<String> catalogs)
        throws SQLException {
        String sql = """
            SELECT SCHEMA_NAME FROM information_schema.SCHEMATA
            WHERE SCHEMA_NAME NOT IN ('information_schema','mysql','performance_schema','sys')
            ORDER BY SCHEMA_NAME
            """;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                addBusinessNamespace(catalogs, resultSet.getString(1));
            }
        }
    }

    private void addBusinessNamespace(Set<String> catalogs, String catalog) {
        if (catalog == null || catalog.isBlank()) {
            return;
        }
        if (SYSTEM_NAMESPACES.contains(catalog.toLowerCase(Locale.ROOT))) {
            return;
        }
        catalogs.add(catalog);
    }
}
