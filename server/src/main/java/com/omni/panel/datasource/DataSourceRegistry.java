package com.omni.panel.datasource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.stereotype.Component;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.dialect.DialectPlugin;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.entity.DataSourceEntity;

/**
 * 按数据源标识管理只读 HikariCP 连接池，并负责连接测试、启动预热与资源释放。
 */
@Component
public class DataSourceRegistry {
    private final CredentialCrypto crypto;
    private final DialectRegistry dialectRegistry;
    private final ConcurrentHashMap<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    public DataSourceRegistry(CredentialCrypto crypto, DialectRegistry dialectRegistry) {
        this.crypto = crypto;
        this.dialectRegistry = dialectRegistry;
    }

    /**
     * 获取数据源对应的共享连接池，首次访问时延迟创建。
     *
     * @param source 数据源配置
     * @return 可获取只读连接的数据源
     */
    public DataSource get(DataSourceEntity source) {
        return pools.computeIfAbsent(source.getId(), ignored -> create(source));
    }

    /**
     * 创建连接池并主动借还一次连接，使启动后首次查询不必再等待建池。
     *
     * @param source 数据源配置
     * @throws BusinessException 无法建立有效连接时抛出
     */
    public void warmUp(DataSourceEntity source) {
        DataSource dataSource = get(source);
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            if (!connection.isValid(3)) {
                throw new BusinessException("数据源连接不可用");
            }
        } catch (SQLException exception) {
            throw new BusinessException("数据源预热失败：" + exception.getMessage());
        }
    }

    /**
     * 判断指定数据源连接池是否已注册。
     *
     * @param sourceId 数据源标识
     * @return 已缓存时返回 {@code true}
     */
    public boolean contains(long sourceId) {
        return pools.containsKey(sourceId);
    }

    /**
     * 使用最新配置创建连接池并原子替换旧池，替换后立即关闭旧池以释放连接。
     *
     * @param source 最新数据源配置
     */
    public void replace(DataSourceEntity source) {
        HikariDataSource replacement = create(source);
        HikariDataSource previous = pools.put(source.getId(), replacement);
        if (previous != null) {
            previous.close();
        }
    }

    /**
     * 移除并关闭指定数据源的连接池。
     *
     * @param sourceId 数据源标识
     */
    public void remove(long sourceId) {
        HikariDataSource removed = pools.remove(sourceId);
        if (removed != null) {
            removed.close();
        }
    }

    /**
     * 使用临时只读连接池验证数据源配置，验证结束后关闭临时池。
     *
     * @param source 待验证的数据源配置
     * @throws BusinessException 无法建立有效连接时抛出
     */
    public void testConnection(DataSourceEntity source) {
        try (HikariDataSource temporary = create(source); Connection connection = temporary.getConnection()) {
            connection.setReadOnly(true);
            if (!connection.isValid(3)) {
                throw new BusinessException("数据源连接不可用");
            }
        } catch (SQLException exception) {
            throw new BusinessException("数据源连接失败：" + exception.getMessage());
        }
    }

    /**
     * 获取已注册且未关闭的连接池。
     */
    public Optional<HikariDataSource> findPool(long sourceId) {
        HikariDataSource pool = pools.get(sourceId);
        if (pool == null || pool.isClosed()) {
            return Optional.empty();
        }
        return Optional.of(pool);
    }

    /**
     * 探测数据源可用性与延迟，并附带连接池运行指标。
     *
     * @param source 数据源配置
     * @return 探测结果
     */
    public HealthProbe probe(DataSourceEntity source) {
        long startedNs = System.nanoTime();
        DialectPlugin dialect = dialectRegistry.resolve(source);
        String healthSql = dialect.healthCheckSql();
        Optional<HikariDataSource> existing = findPool(source.getId());
        if (existing.isPresent()) {
            HikariDataSource pool = existing.get();
            try (Connection connection = pool.getConnection();
                 Statement statement = connection.createStatement()) {
                connection.setReadOnly(true);
                statement.setQueryTimeout(3);
                statement.execute(healthSql);
                long latencyMs = elapsedMs(startedNs);
                return HealthProbe.up(true, latencyMs, null, poolMetrics(pool));
            } catch (Exception exception) {
                return HealthProbe.down(true, elapsedMs(startedNs), exception.getMessage(), poolMetrics(pool));
            }
        }
        try {
            dialect.validateJdbcUrl(source.getJdbcUrl());
            try (Connection connection = DriverManager.getConnection(
                    source.getJdbcUrl(),
                    source.getUsername(),
                    crypto.decrypt(source.getEncryptedPassword())
            );
                 Statement statement = connection.createStatement()) {
                connection.setReadOnly(true);
                statement.setQueryTimeout(3);
                statement.execute(healthSql);
                return HealthProbe.up(false, elapsedMs(startedNs), null, PoolMetrics.empty(null));
            }
        } catch (Exception exception) {
            return HealthProbe.down(false, elapsedMs(startedNs), exception.getMessage(), PoolMetrics.empty(null));
        }
    }

    /**
     * 将纳秒计时起点换算为毫秒耗时。
     *
     * @param startedNs {@link System#nanoTime()} 起点
     * @return 非负毫秒数
     */
    private static long elapsedMs(long startedNs) {
        return Math.max(0L, (System.nanoTime() - startedNs) / 1_000_000L);
    }

    /**
     * 从 Hikari 连接池读取当前运行指标；池已关闭或无 MXBean 时返回部分字段。
     *
     * @param pool Hikari 连接池
     * @return 连接池指标快照
     */
    private static PoolMetrics poolMetrics(HikariDataSource pool) {
        if (pool == null || pool.isClosed()) {
            return PoolMetrics.empty(null);
        }
        HikariPoolMXBean mxBean = pool.getHikariPoolMXBean();
        if (mxBean == null) {
            return new PoolMetrics(pool.getMaximumPoolSize(), pool.getMinimumIdle(), null, null, null, null);
        }
        return new PoolMetrics(
                pool.getMaximumPoolSize(),
                pool.getMinimumIdle(),
                mxBean.getActiveConnections(),
                mxBean.getIdleConnections(),
                mxBean.getTotalConnections(),
                mxBean.getThreadsAwaitingConnection()
        );
    }

    /**
     * 连接池瞬时指标。
     */
    public record PoolMetrics(
            Integer maximumPoolSize,
            Integer minimumIdle,
            Integer activeConnections,
            Integer idleConnections,
            Integer totalConnections,
            Integer threadsAwaitingConnection
    ) {
        static PoolMetrics empty(HikariDataSource pool) {
            if (pool == null) {
                return new PoolMetrics(null, null, null, null, null, null);
            }
            return new PoolMetrics(pool.getMaximumPoolSize(), pool.getMinimumIdle(), null, null, null, null);
        }
    }

    /**
     * 健康探测结果。
     *
     * @param available 是否可用
     * @param poolReady 是否已有常驻连接池
     * @param latencyMs 探测延迟毫秒
     * @param message   失败信息
     * @param metrics   连接池指标
     */
    public record HealthProbe(
            boolean available,
            boolean poolReady,
            Long latencyMs,
            String message,
            PoolMetrics metrics
    ) {
        static HealthProbe up(boolean poolReady, long latencyMs, String message, PoolMetrics metrics) {
            return new HealthProbe(true, poolReady, latencyMs, message, metrics);
        }

        static HealthProbe down(boolean poolReady, long latencyMs, String message, PoolMetrics metrics) {
            return new HealthProbe(false, poolReady, latencyMs, message, metrics);
        }
    }

    /**
     * 创建强制只读会话的连接池，并保留最少一条空闲连接供后续查询复用。
     */
    private HikariDataSource create(DataSourceEntity source) {
        DialectPlugin dialect = dialectRegistry.resolve(source);
        dialect.validateJdbcUrl(source.getJdbcUrl());
        HikariConfig config = new HikariConfig();
        config.setPoolName("analysis-" + source.getId());
        config.setJdbcUrl(source.getJdbcUrl());
        config.setUsername(source.getUsername());
        config.setPassword(crypto.decrypt(source.getEncryptedPassword()));
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(3000);
        config.setReadOnly(true);
        config.setAutoCommit(true);
        dialect.configurePool(config, source);
        return new HikariDataSource(config);
    }

    /**
     * 应用关闭前释放注册表维护的全部连接池。
     */
    @PreDestroy
    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
