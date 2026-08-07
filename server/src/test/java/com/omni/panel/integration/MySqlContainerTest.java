package com.omni.panel.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MySQLContainer;

/**
 * 可选的 Docker/MySQL 联通冒烟；默认跳过。
 * <p>需要本机 Docker 时执行：{@code mvn test -Domni.test.docker=true}
 */
@EnabledIfSystemProperty(named = "omni.test.docker", matches = "true")
class MySqlContainerTest {
    private static MySQLContainer<?> mysql;

    @BeforeAll
    static void startMysql() {
        mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("omni_test");
        mysql.start();
    }

    @AfterAll
    static void stopMysql() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Test
    @DisplayName("Testcontainers MySQL 可建立只读连接")
    void connectsToMysql() throws Exception {
        try (var connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
            connection.setReadOnly(true);
            assertTrue(connection.isValid(3));
        }
    }
}
