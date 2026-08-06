package com.omni.panel.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.omni.panel.datasource.DataSourceRegistry;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.datasource.dialect.MysqlDialectPlugin;
import com.omni.panel.entity.DataSourceEntity;

class JdbcQueryExecutorTest {
    private final DataSourceEntity source = sourceEntity();

    @Test
    void 重复列名生成稳定唯一键且行使用对象结构() {
        JdbcQueryExecutor executor = executor("query-result", 1000);

        JdbcQueryExecutor.QueryResult result =
                executor.execute("query-1", 1, source, "SELECT 1 AS name, 2 AS name", List.of());

        assertThat(result.columns()).containsExactly("NAME", "NAME_2");
        assertThat(result.rows().getFirst()).containsEntry("NAME", 1).containsEntry("NAME_2", 2);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    @DisplayName("未触顶时不跑 COUNT，total 等于行数")
    void totalEqualsRowsWhenBelowCap() throws Exception {
        JdbcDataSource dataSource = memoryDataSource("below-cap");
        seedNumbers(dataSource, 5);
        JdbcQueryExecutor executor = executor(dataSource, 10);

        JdbcQueryExecutor.QueryResult result =
                executor.execute("q-below", 1, source, "SELECT n FROM numbers ORDER BY n", List.of());

        assertThat(result.rows()).hasSize(5);
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    @DisplayName("触顶时 total 来自 COUNT 包装查询")
    void totalFromCountWhenCapped() throws Exception {
        JdbcDataSource dataSource = memoryDataSource("at-cap");
        seedNumbers(dataSource, 20);
        JdbcQueryExecutor executor = executor(dataSource, 5);

        JdbcQueryExecutor.QueryResult result =
                executor.execute("q-cap", 1, source, "SELECT n FROM numbers ORDER BY n", List.of());

        assertThat(result.rows()).hasSize(5);
        assertThat(result.truncated()).isTrue();
        assertThat(result.total()).isEqualTo(20);
    }

    @Test
    @DisplayName("COUNT 失败时降级为已返回行数且 truncated 仍为 true")
    void degradesWhenCountFails() throws Exception {
        JdbcDataSource dataSource = memoryDataSource("count-fail");
        seedNumbers(dataSource, 12);
        JdbcQueryExecutor executor = executor(dataSource, 4);

        JdbcQueryExecutor.QueryResult result = executor.execute(
                "q-fail", 1, source,
                "SELECT n FROM numbers ORDER BY n", List.of(),
                "SELECT COUNT(*) FROM definitely_missing_table", List.of());

        assertThat(result.rows()).hasSize(4);
        assertThat(result.truncated()).isTrue();
        assertThat(result.total()).isEqualTo(4);
    }

    @Test
    @DisplayName("wrapCountSql 去掉末尾分号")
    void wrapCountSqlStripsTrailingSemicolon() {
        assertThat(JdbcQueryExecutor.wrapCountSql("SELECT 1;"))
                .isEqualTo("SELECT COUNT(*) FROM (SELECT 1) omni_cnt");
    }

    private JdbcQueryExecutor executor(String dbName, int maxRows) {
        return executor(memoryDataSource(dbName), maxRows);
    }

    private JdbcQueryExecutor executor(JdbcDataSource dataSource, int maxRows) {
        DataSourceRegistry registry = mock(DataSourceRegistry.class);
        when(registry.get(source)).thenReturn(dataSource);
        DialectRegistry dialectRegistry = new DialectRegistry(List.of(new MysqlDialectPlugin()));
        return new JdbcQueryExecutor(registry, dialectRegistry,
                new QueryProperties(5, maxRows, 1, 1, 1024));
    }

    private static JdbcDataSource memoryDataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static DataSourceEntity sourceEntity() {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setId(1L);
        entity.setDialect("MYSQL");
        return entity;
    }

    private static void seedNumbers(JdbcDataSource dataSource, int count) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS numbers");
            statement.execute("CREATE TABLE numbers (n INT)");
            for (int i = 1; i <= count; i++) {
                statement.execute("INSERT INTO numbers(n) VALUES (" + i + ")");
            }
        }
    }
}
