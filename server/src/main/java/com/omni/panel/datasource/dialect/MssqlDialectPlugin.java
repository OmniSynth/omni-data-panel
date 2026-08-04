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
 * Microsoft SQL Server 方言插件。
 *
 * <p>连接字段中的「默认库」写入 {@code databaseName}；元数据命名空间对应 schema（如 dbo）。</p>
 */
@Component
public class MssqlDialectPlugin extends DialectPlugin {
    public static final String CODE = "MSSQL";

    private static final Set<String> SYSTEM_NAMESPACES = Set.of(
            "sys", "information_schema", "guest", "db_owner", "db_accessadmin",
            "db_securityadmin", "db_ddladmin", "db_backupoperator", "db_datareader",
            "db_datawriter", "db_denydatareader", "db_denydatawriter");

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)^jdbc:sqlserver://([^\\\\,:;]+)(?::(\\d+))?(?:\\\\([^;]+))?(;.*)?$");

    private static final Pattern DB_NAME = Pattern.compile("(?i)(?:^|;)database(?:name)?=([^;]+)");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "SQL Server";
    }

    @Override
    public int defaultPort() {
        return 1433;
    }

    @Override
    public boolean matchesJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return false;
        }
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        return lower.startsWith("jdbc:sqlserver:") || lower.startsWith("jdbc:jtds:sqlserver:");
    }

    @Override
    public String buildJdbcUrl(String host, int port, String defaultDatabase) {
        String normalizedHost = JdbcConnectionFields.requireHost(host);
        JdbcConnectionFields.requirePort(port);
        String database = JdbcConnectionFields.blankToNull(defaultDatabase);
        if (database == null) {
            throw new BusinessException("SQL Server 请填写数据库名");
        }
        if (database.contains(";") || database.contains("=")) {
            throw new BusinessException("数据库名不合法");
        }
        return "jdbc:sqlserver://" + normalizedHost + ':' + port
                + ";databaseName=" + database
                + ";encrypt=true;trustServerCertificate=true";
    }

    @Override
    public ParsedJdbcUrl parseJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        String trimmed = jdbcUrl.trim();
        if (!matchesJdbcUrl(trimmed)) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }
        String host = matcher.group(1);
        int port = matcher.group(2) == null ? defaultPort() : Integer.parseInt(matcher.group(2));
        String database = null;
        Matcher db = DB_NAME.matcher(trimmed);
        if (db.find()) {
            database = JdbcConnectionFields.blankToNull(db.group(1));
        }
        return new ParsedJdbcUrl(host, port, database);
    }

    @Override
    public void validateJdbcUrl(String jdbcUrl) {
        ParsedJdbcUrl parsed = parseJdbcUrl(jdbcUrl);
        if (parsed == null || parsed.defaultDatabase() == null) {
            throw new BusinessException("仅支持带 databaseName 的单个 SQL Server JDBC URL");
        }
    }

    @Override
    public void configurePool(HikariConfig config, DataSourceEntity source) {
        config.setConnectionTestQuery("SELECT 1");
    }

    @Override
    public void prepareConnection(Connection connection, DataSourceEntity source) throws SQLException {
        if (connection.getSchema() == null || connection.getSchema().isBlank()) {
            connection.setSchema("dbo");
        }
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
            return;
        }
        connection.setSchema("dbo");
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
            collectFromSysSchemas(connection, schemas);
        }
        if (schemas.isEmpty()) {
            addBusinessNamespace(schemas, "dbo");
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
                SELECT t.name AS table_name, CAST(ep.value AS nvarchar(4000)) AS table_comment
                FROM sys.tables t
                JOIN sys.schemas s ON s.schema_id = t.schema_id
                LEFT JOIN sys.extended_properties ep
                    ON ep.major_id = t.object_id AND ep.minor_id = 0 AND ep.name = 'MS_Description'
                WHERE s.name = ?
                UNION ALL
                SELECT v.name, CAST(ep.value AS nvarchar(4000))
                FROM sys.views v
                JOIN sys.schemas s ON s.schema_id = v.schema_id
                LEFT JOIN sys.extended_properties ep
                    ON ep.major_id = v.object_id AND ep.minor_id = 0 AND ep.name = 'MS_Description'
                WHERE s.name = ?
                ORDER BY table_name
                """;
        List<DialectTableInfo> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, namespace);
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
                SELECT c.name AS column_name,
                       ty.name AS data_type,
                       c.max_length,
                       c.precision,
                       c.scale,
                       c.is_nullable,
                       c.column_id
                FROM sys.columns c
                JOIN sys.objects o ON o.object_id = c.object_id
                JOIN sys.schemas s ON s.schema_id = o.schema_id
                JOIN sys.types ty ON ty.user_type_id = c.user_type_id
                WHERE s.name = ? AND o.name = ?
                ORDER BY c.column_id
                """;
        List<DialectColumnInfo> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(new DialectColumnInfo(
                            resultSet.getString("column_name"),
                            resultSet.getString("data_type"),
                            intOrNull(resultSet.getObject("precision")),
                            intOrNull(resultSet.getObject("scale")),
                            resultSet.getBoolean("is_nullable"),
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
        return '[';
    }

    @Override
    public String quoteIdentifier(String value) {
        return '[' + value.replace("]", "]]") + ']';
    }

    @Override
    public String limitPlaceholder() {
        return "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
    }

    private void collectFromSysSchemas(Connection connection, Set<String> schemas) throws SQLException {
        String sql = """
                SELECT name FROM sys.schemas
                WHERE name NOT IN (
                    'sys','INFORMATION_SCHEMA','guest','db_owner','db_accessadmin','db_securityadmin',
                    'db_ddladmin','db_backupoperator','db_datareader','db_datawriter',
                    'db_denydatareader','db_denydatawriter'
                )
                ORDER BY name
                """;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                addBusinessNamespace(schemas, resultSet.getString(1));
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
