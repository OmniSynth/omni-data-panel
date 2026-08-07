package com.omni.panel.datasource.dialect;

import org.springframework.stereotype.Component;
import com.omni.panel.common.BusinessException;

/**
 * Apache Spark SQL 方言插件（Spark Thrift Server / HiveServer2 JDBC）。
 * 与 Hive 共用 {@code jdbc:hive2://} 协议；自动探测仍归属 {@link HiveDialectPlugin}，
 * 仅当数据源显式选择 {@code SPARK} 时使用本插件。
 */
@Component
public class SparkDialectPlugin extends HiveDialectPlugin {
    public static final String CODE = "SPARK";

    /**
     * {@inheritDoc}
     */
    @Override
    public String code() {
        return CODE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String label() {
        return "Spark";
    }

    /**
     * 不参与 URL 自动探测，避免与 Hive 争抢 {@code jdbc:hive2} 前缀。
     */
    @Override
    public boolean matchesJdbcUrl(String jdbcUrl) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateJdbcUrl(String jdbcUrl) {
        if (parseJdbcUrl(jdbcUrl) == null
                || jdbcUrl == null
                || !jdbcUrl.matches("(?i)^jdbc:hive2://[^;\\s]+$")) {
            throw new BusinessException("仅支持单个 Spark JDBC URL（jdbc:hive2://...）");
        }
    }
}
