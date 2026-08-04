package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class MybatisPlusConfigContractTest {
    @Test
    void 未显式指定的主键使用数据库自增策略() throws Exception {
        PropertySource<?> application = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .getFirst();

        assertThat(application.getProperty("mybatis-plus.global-config.db-config.id-type"))
                .isEqualTo("auto");
    }
}
