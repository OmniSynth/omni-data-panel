package com.omni.panel.datasource.dialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.DataSourceEntity;

class DialectRegistryTest {
    private final DialectRegistry registry = new DialectRegistry(List.of(
            new MysqlDialectPlugin(),
            new MariaDbDialectPlugin(),
            new PostgresqlDialectPlugin(),
            new MssqlDialectPlugin(),
            new OracleDialectPlugin(),
            new ClickHouseDialectPlugin(),
            new HiveDialectPlugin()));

    @Test
    void 列出已注册方言() {
        assertThat(registry.list()).extracting(DialectInfo::code)
                .containsExactly("CLICKHOUSE", "HIVE", "MARIADB", "MSSQL", "MYSQL", "ORACLE", "POSTGRESQL");
    }

    @Test
    void 空编码默认MYSQL() {
        assertThat(registry.require(null).code()).isEqualTo("MYSQL");
        assertThat(registry.normalize("")).isEqualTo("MYSQL");
    }

    @Test
    void 未知方言拒绝() {
        assertThatThrownBy(() -> registry.require("DB2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的数据源方言");
    }

    @Test
    void 按JdbcUrl探测() {
        assertThat(registry.detect("jdbc:mysql://localhost:3306").code()).isEqualTo("MYSQL");
        assertThat(registry.detect("jdbc:mariadb://localhost:3306").code()).isEqualTo("MARIADB");
        assertThat(registry.detect("jdbc:postgresql://localhost:5432/app").code()).isEqualTo("POSTGRESQL");
        assertThat(registry.detect("jdbc:sqlserver://localhost:1433;databaseName=app").code()).isEqualTo("MSSQL");
        assertThat(registry.detect("jdbc:oracle:thin:@//localhost:1521/ORCL").code()).isEqualTo("ORACLE");
        assertThat(registry.detect("jdbc:clickhouse://localhost:8123/default").code()).isEqualTo("CLICKHOUSE");
        assertThat(registry.detect("jdbc:hive2://localhost:10000/default").code()).isEqualTo("HIVE");
    }

    @Test
    void 按实体方言解析() {
        DataSourceEntity source = new DataSourceEntity();
        source.setDialect("mssql");
        assertThat(registry.resolve(source).code()).isEqualTo("MSSQL");
        source.setDialect("oracle");
        assertThat(registry.resolve(source).code()).isEqualTo("ORACLE");
        source.setDialect("clickhouse");
        assertThat(registry.resolve(source).code()).isEqualTo("CLICKHOUSE");
        source.setDialect("hive");
        assertThat(registry.resolve(source).code()).isEqualTo("HIVE");
    }
}
