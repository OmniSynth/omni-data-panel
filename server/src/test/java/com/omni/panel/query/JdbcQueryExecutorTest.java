package com.omni.panel.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import com.omni.panel.datasource.DataSourceRegistry;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.datasource.dialect.MysqlDialectPlugin;
import com.omni.panel.entity.DataSourceEntity;

class JdbcQueryExecutorTest {
    @Test
    void 重复列名生成稳定唯一键且行使用对象结构() {
        DataSourceRegistry registry = mock(DataSourceRegistry.class);
        DataSourceEntity source = new DataSourceEntity();
        source.setId(1L);
        source.setDialect("MYSQL");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:query-result;MODE=MySQL");
        when(registry.get(source)).thenReturn(dataSource);
        DialectRegistry dialectRegistry = new DialectRegistry(List.of(new MysqlDialectPlugin()));
        JdbcQueryExecutor executor = new JdbcQueryExecutor(registry, dialectRegistry,
                new QueryProperties(5, 1000, 1, 1, 1024));

        JdbcQueryExecutor.QueryResult result =
                executor.execute("query-1", 1, source, "SELECT 1 AS name, 2 AS name", List.of());

        assertThat(result.columns()).containsExactly("NAME", "NAME_2");
        assertThat(result.rows().getFirst()).containsEntry("NAME", 1).containsEntry("NAME_2", 2);
    }
}
