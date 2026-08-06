package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class QuartzJdbcMigrationContractTest {
    @Test
    void V16迁移创建Quartz集群表且不包含DROP() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V16__quartz_jdbc.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains(
                    "CREATE TABLE QRTZ_JOB_DETAILS",
                    "CREATE TABLE QRTZ_TRIGGERS",
                    "CREATE TABLE QRTZ_CRON_TRIGGERS",
                    "CREATE TABLE QRTZ_FIRED_TRIGGERS",
                    "CREATE TABLE QRTZ_SCHEDULER_STATE",
                    "CREATE TABLE QRTZ_LOCKS");
            assertThat(sql).doesNotContain("DROP TABLE");
        }
    }

    @Test
    void 应用配置启用JDBC集群JobStore() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertThat(yml).contains(
                "job-store-type: jdbc",
                "initialize-schema: never",
                "overwrite-existing-jobs: true",
                "org.quartz.jobStore.isClustered: true",
                "org.quartz.scheduler.instanceId: AUTO",
                "org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        assertThat(yml).doesNotContain("job-store-type: memory");
    }
}
