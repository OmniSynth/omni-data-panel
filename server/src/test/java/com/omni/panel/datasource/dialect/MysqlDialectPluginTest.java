package com.omni.panel.datasource.dialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MysqlDialectPluginTest {
    private final MysqlDialectPlugin mysql = new MysqlDialectPlugin();

    @Test
    void 组装带库名与不带库名的地址() {
        assertThat(mysql.buildJdbcUrl("127.0.0.1", 3306, null))
                .isEqualTo("jdbc:mysql://127.0.0.1:3306");
        assertThat(mysql.buildJdbcUrl("db.local", 3307, "sales"))
                .isEqualTo("jdbc:mysql://db.local:3307/sales");
    }

    @Test
    void 解析旧JdbcUrl() {
        ParsedJdbcUrl parsed = mysql.parseJdbcUrl("jdbc:mysql://10.0.0.1:3306/bi_demo?useSSL=false");
        assertThat(parsed).isNotNull();
        assertThat(parsed.host()).isEqualTo("10.0.0.1");
        assertThat(parsed.port()).isEqualTo(3306);
        assertThat(parsed.defaultDatabase()).isEqualTo("bi_demo");
    }

    @Test
    void 解析无库名地址() {
        ParsedJdbcUrl parsed = mysql.parseJdbcUrl("jdbc:mysql://localhost:3306");
        assertThat(parsed).isNotNull();
        assertThat(parsed.host()).isEqualTo("localhost");
        assertThat(parsed.port()).isEqualTo(3306);
        assertThat(parsed.defaultDatabase()).isNull();
    }

    @Test
    void 端口非法时拒绝() {
        assertThatThrownBy(() -> JdbcConnectionFields.requirePort(0))
                .hasMessageContaining("端口");
    }
}
