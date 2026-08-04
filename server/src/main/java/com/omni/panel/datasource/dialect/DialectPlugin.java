package com.omni.panel.datasource.dialect;

import com.omni.panel.datasource.DataSourceEntity;
import com.zaxxer.hikari.HikariConfig;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 分析数据源方言插件：负责 JDBC URL、连接池、命名空间与 SQL 方言细节。
 *
 * <p>使用抽象类而非接口，避免被 {@code @MapperScan("com.omni.panel")} 误识别为 MyBatis Mapper。</p>
 */
public abstract class DialectPlugin {
    /** 方言编码，如 MYSQL。 */
    public abstract String code();

    /** 管理端显示名称。 */
    public abstract String label();

    /** 新建数据源时的默认端口。 */
    public abstract int defaultPort();

    /** 是否匹配该 JDBC URL。 */
    public abstract boolean matchesJdbcUrl(String jdbcUrl);

    /** 组装 JDBC URL。 */
    public abstract String buildJdbcUrl(String host, int port, String defaultDatabase);

    /**
     * 解析 JDBC URL；无法解析时返回 null。
     */
    public abstract ParsedJdbcUrl parseJdbcUrl(String jdbcUrl);

    /** 校验 JDBC URL 是否可被本方言接受。 */
    public abstract void validateJdbcUrl(String jdbcUrl);

    /**
     * 配置方言相关的连接池参数（如 connectionInitSql）。
     * 通用池大小由注册表设置。
     */
    public abstract void configurePool(HikariConfig config, DataSourceEntity source);

    /** 健康探测 SQL。 */
    public String healthCheckSql() {
        return "SELECT 1";
    }

    /** 执行查询前准备连接（如 setCatalog）。 */
    public abstract void prepareConnection(Connection connection, DataSourceEntity source) throws SQLException;

    /**
     * 归还连接前恢复命名空间。
     *
     * @param preferredNamespace 优先恢复的默认库
     * @param originalNamespace 借用连接时的原始 catalog/schema
     */
    public abstract void restoreConnection(Connection connection, String preferredNamespace, String originalNamespace)
        throws SQLException;

    /** 切换到指定业务命名空间（库/schema）。 */
    public abstract void useNamespace(Connection connection, String namespace) throws SQLException;

    /**
     * 列出可同步的业务命名空间（已排除系统库）。
     */
    public abstract List<String> listNamespaces(Connection connection, DatabaseMetaData metadata) throws SQLException;

    /** 系统命名空间，同步时跳过。 */
    public abstract Set<String> systemNamespaces();

    /**
     * DatabaseMetaData 拿不到表时的回退列表；无回退时返回空列表。
     */
    public List<DialectTableInfo> listTablesFallback(Connection connection, String namespace)
        throws SQLException {
        return List.of();
    }

    /**
     * DatabaseMetaData 拿不到列时的回退列表；无回退时返回空列表。
     */
    public List<DialectColumnInfo> listColumnsFallback(Connection connection, String namespace, String table)
        throws SQLException {
        return List.of();
    }

    /** 标识符引号字符。 */
    public abstract char identifierQuote();

    /** 引用标识符。 */
    public String quoteIdentifier(String value) {
        char quote = identifierQuote();
        return quote + value + quote;
    }

    /** 追加行数限制占位片段，默认 {@code LIMIT ?}。 */
    public String limitPlaceholder() {
        return "LIMIT ?";
    }

    /** 方言特有的禁止 SQL 模式（已小写匹配用）。 */
    public List<Pattern> forbiddenSqlPatterns() {
        return List.of();
    }
}
