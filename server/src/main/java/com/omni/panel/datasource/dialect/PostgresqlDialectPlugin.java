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
 * PostgreSQL 分析数据源方言插件。
 *
 * <p>连接字段中的「默认库」对应 JDBC URL 中的 database；元数据命名空间对应 schema（如 public）。</p>
 */
@Component
public class PostgresqlDialectPlugin extends DialectPlugin {
    public static final String CODE = "POSTGRESQL";

    private static final Set<String> SYSTEM_NAMESPACES = Set.of(
            "information_schema", "pg_catalog", "pg_toast");

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)^jdbc:postgresql://([^/:?]+)(?::(\\d+))?(?:/([^?;\\s]*))?.*$");

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("(?s).*\\bcopy\\s+.*\\bto\\s+program\\b.*"),
            Pattern.compile("(?s).*\\bpg_read_file\\s*\\(.*"),
            Pattern.compile("(?s).*\\bpg_write_file\\s*\\(.*"),
            Pattern.compile("(?s).*\\blo_import\\s*\\(.*"),
            Pattern.compile("(?s).*\\blo_export\\s*\\(.*")
    );

    /**
     * {@inheritDoc}
     */
    @Override
    public String code() {
        return CODE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String label() {
        return "PostgreSQL";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int defaultPort() {
        return 5432;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matchesJdbcUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String buildJdbcUrl(String host, int port, String defaultDatabase) {
        String normalizedHost = JdbcConnectionFields.requireHost(host);
        JdbcConnectionFields.requirePort(port);
        String database = JdbcConnectionFields.blankToNull(defaultDatabase);
        if (database == null) {
            throw new BusinessException("PostgreSQL 请填写数据库名");
        }
        if (database.contains("/") || database.contains("?") || database.contains(";")) {
            throw new BusinessException("数据库名不合法");
        }
        return "jdbc:postgresql://" + normalizedHost + ':' + port + '/' + database;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateJdbcUrl(String jdbcUrl) {
        ParsedJdbcUrl parsed = parseJdbcUrl(jdbcUrl);
        if (parsed == null || parsed.defaultDatabase() == null) {
            throw new BusinessException("仅支持带数据库名的单个 PostgreSQL JDBC URL");
        }
        if (jdbcUrl == null || !jdbcUrl.matches("(?i)^jdbc:postgresql://[^;\\s]+$")) {
            throw new BusinessException("仅支持带数据库名的单个 PostgreSQL JDBC URL");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void configurePool(HikariConfig config, DataSourceEntity source) {
        config.setConnectionInitSql("SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepareConnection(Connection connection, DataSourceEntity source) throws SQLException {
        // 库名已在 URL 中；默认搜索路径保留 public，跨 schema 用 schema.table
        if (connection.getSchema() == null || connection.getSchema().isBlank()) {
            connection.setSchema("public");
        }
    }

    /**
     * {@inheritDoc}
     */
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
        connection.setSchema("public");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void useNamespace(Connection connection, String namespace) throws SQLException {
        connection.setSchema(namespace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean defaultDatabaseIsNamespace() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String metaCatalog(String namespace) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String metaSchema(String namespace) {
        return namespace;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> listNamespaces(Connection connection, DatabaseMetaData metadata) throws SQLException {
        Set<String> schemas = new LinkedHashSet<>();
        collectFromMetaData(metadata, schemas);
        if (schemas.isEmpty()) {
            collectFromInformationSchema(connection, schemas);
        }
        if (schemas.isEmpty()) {
            addBusinessNamespace(schemas, connection.getSchema());
            addBusinessNamespace(schemas, "public");
        }
        return List.copyOf(schemas);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> systemNamespaces() {
        return SYSTEM_NAMESPACES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DialectTableInfo> listTablesFallback(Connection connection, String namespace)
            throws SQLException {
        String sql = """
                SELECT c.relname AS table_name, d.description AS table_comment
                FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_catalog.pg_description d ON d.objoid = c.oid AND d.objsubid = 0
                WHERE n.nspname = ?
                  AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
                ORDER BY c.relname
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

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DialectColumnInfo> listColumnsFallback(Connection connection, String namespace, String table)
            throws SQLException {
        String sql = """
                SELECT column_name,
                       data_type,
                       character_maximum_length,
                       numeric_precision,
                       numeric_scale,
                       is_nullable,
                       ordinal_position
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """;
        List<DialectColumnInfo> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Integer columnSize = intOrNull(resultSet.getObject("character_maximum_length"));
                    if (columnSize == null) {
                        columnSize = intOrNull(resultSet.getObject("numeric_precision"));
                    }
                    columns.add(new DialectColumnInfo(
                            resultSet.getString("column_name"),
                            resultSet.getString("data_type"),
                            columnSize,
                            intOrNull(resultSet.getObject("numeric_scale")),
                            "YES".equalsIgnoreCase(resultSet.getString("is_nullable")),
                            resultSet.getInt("ordinal_position"),
                            null
                    ));
                }
            }
        }
        return columns;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char identifierQuote() {
        return '"';
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String quoteIdentifier(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Pattern> forbiddenSqlPatterns() {
        return FORBIDDEN;
    }

    /**
     * 将 JDBC 数值对象转为 Integer；非数值时返回 null。
     *
     * @param value 结果集字段值
     * @return 整数值或 null
     */
    /**
     * 将 JDBC 数值对象转为 Integer；非数值时返回 null。
     *
     * @param value 结果集字段值
     * @return 整数值或 null
     */
    private static Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    /**
     * 通过 {@link DatabaseMetaData#getSchemas()} 收集业务 schema。
     *
     * @param metadata JDBC 元数据
     * @param schemas  待填充的 schema 集合
     */
    /**
     * 通过 {@link DatabaseMetaData#getSchemas()} 收集业务 schema。
     *
     * @param metadata JDBC 元数据
     * @param schemas  待填充的 schema 集合
     */
    private void collectFromMetaData(DatabaseMetaData metadata, Set<String> schemas) throws SQLException {
        try (ResultSet resultSet = metadata.getSchemas()) {
            while (resultSet.next()) {
                addBusinessNamespace(schemas, resultSet.getString("TABLE_SCHEM"));
            }
        }
    }

    /**
     * 通过 information_schema.schemata 收集业务 schema。
     *
     * @param connection JDBC 连接
     * @param schemas    待填充的 schema 集合
     */
    /**
     * 通过 information_schema.schemata 收集业务 schema。
     *
     * @param connection JDBC 连接
     * @param schemas    待填充的 schema 集合
     */
    private void collectFromInformationSchema(Connection connection, Set<String> schemas) throws SQLException {
        String sql = """
                SELECT schema_name FROM information_schema.schemata
                WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
                  AND schema_name NOT LIKE 'pg\\_toast%' ESCAPE '\\'
                  AND schema_name NOT LIKE 'pg\\_temp%' ESCAPE '\\'
                  AND schema_name NOT LIKE 'pg\\_toast\\_temp%' ESCAPE '\\'
                ORDER BY schema_name
                """;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                addBusinessNamespace(schemas, resultSet.getString(1));
            }
        }
    }

    /**
     * 将非系统 schema 加入集合，忽略空值与 PostgreSQL 系统命名空间。
     *
     * @param schemas 业务 schema 集合
     * @param schema  待判定的 schema 名
     */
    /**
     * 将非系统 schema 加入集合，忽略空值与 PostgreSQL 系统命名空间。
     *
     * @param schemas 业务 schema 集合
     * @param schema  待判定的 schema 名
     */
    private void addBusinessNamespace(Set<String> schemas, String schema) {
        if (schema == null || schema.isBlank()) {
            return;
        }
        String normalized = schema.toLowerCase(Locale.ROOT);
        if (SYSTEM_NAMESPACES.contains(normalized)
                || normalized.startsWith("pg_toast")
                || normalized.startsWith("pg_temp")
                || normalized.startsWith("pg_toast_temp")) {
            return;
        }
        schemas.add(schema);
    }
}
