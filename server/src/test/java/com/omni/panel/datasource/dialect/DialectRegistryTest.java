package com.omni.panel.datasource.dialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.DataSourceEntity;

class DialectRegistryTest {
    private final DialectRegistry registry = new DialectRegistry(List.of(
            new MysqlDialectPlugin(), new MariaDbDialectPlugin()));

    @Test
    void 列出已注册方言() {
        assertThat(registry.list()).extracting(DialectInfo::code)
                .containsExactly("MARIADB", "MYSQL");
    }

    @Test
    void 空编码默认MYSQL() {
        assertThat(registry.require(null).code()).isEqualTo("MYSQL");
        assertThat(registry.normalize("")).isEqualTo("MYSQL");
    }

    @Test
    void 未知方言拒绝() {
        assertThatThrownBy(() -> registry.require("POSTGRESQL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的数据源方言");
    }

    @Test
    void 按JdbcUrl探测() {
        assertThat(registry.detect("jdbc:mysql://localhost:3306").code()).isEqualTo("MYSQL");
        assertThat(registry.detect("jdbc:mariadb://localhost:3306").code()).isEqualTo("MARIADB");
    }

    @Test
    void 按实体方言解析() {
        DataSourceEntity source = new DataSourceEntity();
        source.setDialect("mariadb");
        assertThat(registry.resolve(source).code()).isEqualTo("MARIADB");
    }
}
