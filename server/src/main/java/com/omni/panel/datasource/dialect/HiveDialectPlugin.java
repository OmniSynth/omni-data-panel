package com.omni.panel.datasource.dialect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
 * Apache Hive 方言插件（HiveServer2 JDBC）。命名空间对应 database。
 */
@Component
public class HiveDialectPlugin extends DialectPlugin {
    public static final String CODE = "HIVE";

    private static final Set<String> SYSTEM_NAMESPACES = Set.of(
            "information_schema", "sys");

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)^jdbc:hive2://([^/:?]+)(?::(\\d+))?(?:/([^?;\\s]*))?.*$");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Hive";
    }

    @Override
    public int defaultPort() {
        return 10000;
    }

    @Override
    public boolean matchesJdbcUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:hive2:");
    }

    @Override
    public String buildJdbcUrl(String host, int port, String defaultDatabase) {
        String normalizedHost = JdbcConnectionFields.requireHost(host);
        JdbcConnectionFields.requirePort(port);
        String database = JdbcConnectionFields.blankToNull(defaultDatabase);
        StringBuilder url = new StringBuilder("jdbc:hive2://")
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
        if (parseJdbcUrl(jdbcUrl) == null
                || jdbcUrl == null
                || !jdbcUrl.matches("(?i)^jdbc:hive2://[^;\\s]+$")) {
            throw new BusinessException("仅支持单个 Hive JDBC URL（jdbc:hive2://...）");
        }
    }

    @Override
    public void configurePool(HikariConfig config, DataSourceEntity source) {
        // Hive JDBC 对 isValid 支持不稳定，强制探测 SQL
        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
    }

    @Override
    public void prepareConnection(Connection connection, DataSourceEntity source) throws SQLException {
        String database = source.getDefaultDatabase();
        if (database != null && !database.isBlank()) {
            useNamespace(connection, database.trim());
        }
    }

    @Override
    public void restoreConnection(Connection connection, String preferredNamespace, String originalNamespace)
            throws SQLException {
        String target = preferredNamespace != null && !preferredNamespace.isBlank()
                ? preferredNamespace.trim()
                : originalNamespace;
        if (target != null && !target.isBlank()) {
            useNamespace(connection, target);
        }
    }

    @Override
    public void useNamespace(Connection connection, String namespace) throws SQLException {
        String safe = namespace.replace("`", "").replace(";", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("USE `" + safe + "`");
        }
    }

    @Override
    public List<String> listNamespaces(Connection connection, DatabaseMetaData metadata) throws SQLException {
        Set<String> catalogs = new LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getSchemas()) {
            while (resultSet.next()) {
                addBusinessNamespace(catalogs, resultSet.getString("TABLE_SCHEM"));
            }
        } catch (SQLException ignored) {
            // 部分 Hive 驱动 getSchemas 不可用
        }
        if (catalogs.isEmpty()) {
            try (ResultSet resultSet = metadata.getCatalogs()) {
                while (resultSet.next()) {
                    addBusinessNamespace(catalogs, resultSet.getString(1));
                }
            } catch (SQLException ignored) {
                // ignore
            }
        }
        if (catalogs.isEmpty()) {
            collectFromShowDatabases(connection, catalogs);
        }
        if (catalogs.isEmpty()) {
            addBusinessNamespace(catalogs, "default");
        }
        return List.copyOf(catalogs);
    }

    @Override
    public Set<String> systemNamespaces() {
        return SYSTEM_NAMESPACES;
    }

    @Override
    public String metaCatalog(String namespace) {
        return null;
    }

    @Override
    public String metaSchema(String namespace) {
        return namespace;
    }

    @Override
    public List<DialectTableInfo> listTablesFallback(Connection connection, String namespace)
            throws SQLException {
        useNamespace(connection, namespace);
        List<DialectTableInfo> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW TABLES")) {
            while (resultSet.next()) {
                String name = resultSet.getString(1);
                if (name != null && !name.isBlank()) {
                    tables.add(new DialectTableInfo(name, null));
                }
            }
        }
        return tables;
    }

    @Override
    public List<DialectColumnInfo> listColumnsFallback(Connection connection, String namespace, String table)
            throws SQLException {
        useNamespace(connection, namespace);
        String safeTable = table.replace("`", "").replace(";", "");
        List<DialectColumnInfo> columns = new ArrayList<>();
        int position = 1;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("DESCRIBE `" + safeTable + "`")) {
            while (resultSet.next()) {
                String name = resultSet.getString(1);
                if (name == null || name.isBlank() || name.startsWith("#")) {
                    continue;
                }
                columns.add(new DialectColumnInfo(
                        name.trim(),
                        resultSet.getString(2),
                        null,
                        null,
                        true,
                        position++,
                        resultSet.getMetaData().getColumnCount() >= 3 ? resultSet.getString(3) : null
                ));
            }
        }
        return columns;
    }

    @Override
    public char identifierQuote() {
        return '`';
    }

    private void collectFromShowDatabases(Connection connection, Set<String> catalogs) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW DATABASES")) {
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
