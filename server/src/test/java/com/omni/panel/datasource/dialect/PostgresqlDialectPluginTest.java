package com.omni.panel.datasource.dialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostgresqlDialectPluginTest {
    private final PostgresqlDialectPlugin postgres = new PostgresqlDialectPlugin();

    @Test
    void 组装必须带数据库名() {
        assertThat(postgres.buildJdbcUrl("127.0.0.1", 5432, "sales"))
                .isEqualTo("jdbc:postgresql://127.0.0.1:5432/sales");
        assertThatThrownBy(() -> postgres.buildJdbcUrl("127.0.0.1", 5432, null))
                .hasMessageContaining("数据库名");
    }

    @Test
    void 解析JdbcUrl() {
        ParsedJdbcUrl parsed = postgres.parseJdbcUrl("jdbc:postgresql://10.0.0.1:5432/bi_demo?ssl=false");
        assertThat(parsed).isNotNull();
        assertThat(parsed.host()).isEqualTo("10.0.0.1");
        assertThat(parsed.port()).isEqualTo(5432);
        assertThat(parsed.defaultDatabase()).isEqualTo("bi_demo");
    }

    @Test
    void 无库名地址校验失败() {
        assertThatThrownBy(() -> postgres.validateJdbcUrl("jdbc:postgresql://localhost:5432"))
                .hasMessageContaining("数据库名");
    }

    @Test
    void 默认库不作为唯一同步命名空间() {
        assertThat(postgres.defaultDatabaseIsNamespace()).isFalse();
        assertThat(postgres.metaCatalog("public")).isNull();
        assertThat(postgres.metaSchema("public")).isEqualTo("public");
        assertThat(postgres.identifierQuote()).isEqualTo('"');
        assertThat(postgres.quoteIdentifier("order")).isEqualTo("\"order\"");
    }
}
