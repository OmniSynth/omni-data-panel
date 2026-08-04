package com.omni.panel.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.DriverManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "omni.test.docker", matches = "true")
class MySqlContainerTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("omni_test");

    @Test
    @DisplayName("Testcontainers MySQL 可建立只读连接")
    void connectsToMysql() throws Exception {
        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            connection.setReadOnly(true);
            assertTrue(connection.isValid(3));
        }
    }
}
