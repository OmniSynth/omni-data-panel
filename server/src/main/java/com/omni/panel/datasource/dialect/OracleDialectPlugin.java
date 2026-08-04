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
 * Oracle 方言插件。
 *
 * <p>连接字段中的「默认库」对应服务名（service name）；元数据命名空间对应 schema（用户）。</p>
 */
@Component
public class OracleDialectPlugin extends DialectPlugin {
    public static final String CODE = "ORACLE";

    private static final Set<String> SYSTEM_NAMESPACES = Set.of(
            "sys", "system", "outln", "dbsnmp", "appqossys", "dbsfwuser", "ggshareddc",
            "anouser", "ctxsys", "dvsys", "flows_files", "mdsys", "ordsys",
            "lbacsys", "xdb", "wmsys", "orddata", "olapsys", "gsmadmin_internal",
            "ojsys", "remote_scheduler_agent", "dvf", "aucsys");

    private static final Pattern SERVICE_URL = Pattern.compile(
            "(?i)^jdbc:oracle:thin:@//([^/:]+)(?::(\\d+))?/([^?;\\s]+).*$");
    private static final Pattern SID_URL = Pattern.compile(
            "(?i)^jdbc:oracle:thin:@([^/:]+):(\\d+):([^?;\\s]+).*$");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Oracle";
    }

    @Override
    public int defaultPort() {
        return 1521;
    }

    @Override
    public boolean matchesJdbcUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:oracle:");
    }

    @Override
    public String buildJdbcUrl(String host, int port, String defaultDatabase) {
        String normalizedHost = JdbcConnectionFields.requireHost(host);
        JdbcConnectionFields.requirePort(port);
        String service = JdbcConnectionFields.blankToNull(defaultDatabase);
        if (service == null) {
            throw new BusinessException("Oracle 请填写服务名（Service Name）");
        }
        if (service.contains("/") || service.contains("@") || service.contains(";")) {
            throw new BusinessException("服务名不合法");
        }
        return "jdbc:oracle:thin:@//" + normalizedHost + ':' + port + '/' + service;
    }

    @Override
    public ParsedJdbcUrl parseJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        String trimmed = jdbcUrl.trim();
        Matcher service = SERVICE_URL.matcher(trimmed);
        if (service.matches()) {
            int port = service.group(2) == null ? defaultPort() : Integer.parseInt(service.group(2));
            return new ParsedJdbcUrl(service.group(1), port, JdbcConnectionFields.blankToNull(service.group(3)));
        }
        Matcher sid = SID_URL.matcher(trimmed);
        if (sid.matches()) {
            return new ParsedJdbcUrl(sid.group(1), Integer.parseInt(sid.group(2)),
                    JdbcConnectionFields.blankToNull(sid.group(3)));
        }
        return null;
    }

    @Override
    public void validateJdbcUrl(String jdbcUrl) {
        ParsedJdbcUrl parsed = parseJdbcUrl(jdbcUrl);
        if (parsed == null || parsed.defaultDatabase() == null) {
            throw new BusinessException("仅支持带服务名/SID 的单个 Oracle JDBC URL");
        }
    }

    @Override
    public void configurePool(HikariConfig config, DataSourceEntity source) {
        config.setConnectionTestQuery("SELECT 1 FROM DUAL");
    }

    @Override
    public String healthCheckSql() {
        return "SELECT 1 FROM DUAL";
    }

    @Override
    public void prepareConnection(Connection connection, DataSourceEntity source) throws SQLException {
        // schema 由查询显式限定；保持连接当前用户默认 schema
    }

    @Override
    public void restoreConnection(Connection connection, String preferredNamespace, String originalNamespace)
            throws SQLException {
        if (preferredNamespace != null && !preferredNamespace.isBlank()) {
            connection.setSchema(preferredNamespace.trim());
            return;
        }
        if (originalNamespace != null && !originalNamespace.isBlank()) {
            connection.setSchema(originalNamespace);
        }
    }

    @Override
    public void useNamespace(Connection connection, String namespace) throws SQLException {
        connection.setSchema(namespace);
    }

    @Override
    public boolean defaultDatabaseIsNamespace() {
        return false;
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
    public List<String> listNamespaces(Connection connection, DatabaseMetaData metadata) throws SQLException {
        Set<String> schemas = new LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getSchemas()) {
            while (resultSet.next()) {
                addBusinessNamespace(schemas, resultSet.getString("TABLE_SCHEM"));
            }
        }
        if (schemas.isEmpty()) {
            collectFromAllUsers(connection, schemas);
        }
        if (schemas.isEmpty()) {
            addBusinessNamespace(schemas, connection.getSchema());
            addBusinessNamespace(schemas, connection.getMetaData().getUserName());
        }
        return List.copyOf(schemas);
    }

    @Override
    public Set<String> systemNamespaces() {
        return SYSTEM_NAMESPACES;
    }

    @Override
    public List<DialectTableInfo> listTablesFallback(Connection connection, String namespace)
            throws SQLException {
        String sql = """
                SELECT table_name, NULL AS table_comment
                FROM all_tables
                WHERE owner = ?
                UNION ALL
                SELECT view_name, NULL
                FROM all_views
                WHERE owner = ?
                ORDER BY 1
                """;
        List<DialectTableInfo> tables = new ArrayList<>();
        String owner = namespace.toUpperCase(Locale.ROOT);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, owner);
            statement.setString(2, owner);
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
                SELECT column_name, data_type, data_length, data_precision, data_scale,
                       nullable, column_id
                FROM all_tab_columns
                WHERE owner = ? AND table_name = ?
                ORDER BY column_id
                """;
        List<DialectColumnInfo> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace.toUpperCase(Locale.ROOT));
            statement.setString(2, table.toUpperCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Integer precision = intOrNull(resultSet.getObject("data_precision"));
                    Integer length = intOrNull(resultSet.getObject("data_length"));
                    columns.add(new DialectColumnInfo(
                            resultSet.getString("column_name"),
                            resultSet.getString("data_type"),
                            precision != null ? precision : length,
                            intOrNull(resultSet.getObject("data_scale")),
                            "Y".equalsIgnoreCase(resultSet.getString("nullable")),
                            resultSet.getInt("column_id"),
                            null
                    ));
                }
            }
        }
        return columns;
    }

    @Override
    public char identifierQuote() {
        return '"';
    }

    @Override
    public String quoteIdentifier(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    @Override
    public String limitPlaceholder() {
        return "FETCH FIRST ? ROWS ONLY";
    }

    private void collectFromAllUsers(Connection connection, Set<String> schemas) throws SQLException {
        String sql = """
                SELECT username FROM all_users
                WHERE oracle_maintained = 'N'
                ORDER BY username
                """;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                addBusinessNamespace(schemas, resultSet.getString(1));
            }
        } catch (SQLException ignored) {
            // 旧版本可能无 oracle_maintained 列
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT username FROM all_users ORDER BY username")) {
                while (resultSet.next()) {
                    addBusinessNamespace(schemas, resultSet.getString(1));
                }
            }
        }
    }

    private void addBusinessNamespace(Set<String> schemas, String schema) {
        if (schema == null || schema.isBlank()) {
            return;
        }
        if (SYSTEM_NAMESPACES.contains(schema.toLowerCase(Locale.ROOT))) {
            return;
        }
        schemas.add(schema);
    }

    private static Integer intOrNull(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
