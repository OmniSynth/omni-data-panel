package com.omni.panel.datasource.dialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExtendedDialectPluginTest {
    @Test
    void sqlServer组装与限制语法() {
        MssqlDialectPlugin plugin = new MssqlDialectPlugin();
        assertThat(plugin.buildJdbcUrl("db.host", 1433, "sales"))
                .startsWith("jdbc:sqlserver://db.host:1433;databaseName=sales");
        assertThat(plugin.defaultDatabaseIsNamespace()).isFalse();
        assertThat(plugin.metaSchema("dbo")).isEqualTo("dbo");
        assertThat(plugin.quoteIdentifier("order")).isEqualTo("[order]");
        assertThat(plugin.limitPlaceholder()).contains("FETCH NEXT ?");
        assertThatThrownBy(() -> plugin.buildJdbcUrl("db.host", 1433, null))
                .hasMessageContaining("数据库名");
    }

    @Test
    void oracle组装与限制语法() {
        OracleDialectPlugin plugin = new OracleDialectPlugin();
        assertThat(plugin.buildJdbcUrl("ora.host", 1521, "ORCLPDB1"))
                .isEqualTo("jdbc:oracle:thin:@//ora.host:1521/ORCLPDB1");
        ParsedJdbcUrl sid = plugin.parseJdbcUrl("jdbc:oracle:thin:@ora.host:1521:ORCL");
        assertThat(sid).isNotNull();
        assertThat(sid.defaultDatabase()).isEqualTo("ORCL");
        assertThat(plugin.limitPlaceholder()).isEqualTo("FETCH FIRST ? ROWS ONLY");
        assertThat(plugin.healthCheckSql()).contains("DUAL");
    }

    @Test
    void clickHouse与Hive组装() {
        ClickHouseDialectPlugin clickHouse = new ClickHouseDialectPlugin();
        assertThat(clickHouse.buildJdbcUrl("ch.host", 8123, "analytics"))
                .isEqualTo("jdbc:clickhouse://ch.host:8123/analytics");
        assertThat(clickHouse.defaultDatabaseIsNamespace()).isTrue();
        assertThat(clickHouse.identifierQuote()).isEqualTo('`');

        HiveDialectPlugin hive = new HiveDialectPlugin();
        assertThat(hive.buildJdbcUrl("hive.host", 10000, "ods"))
                .isEqualTo("jdbc:hive2://hive.host:10000/ods");
        assertThat(hive.matchesJdbcUrl("jdbc:hive2://hive.host:10000/ods")).isTrue();
        assertThat(hive.metaSchema("ods")).isEqualTo("ods");
    }
}
