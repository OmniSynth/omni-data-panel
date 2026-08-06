package com.omni.panel.query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.DataSourceRegistry;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.entity.DataSourceEntity;

/**
 * 在受管数据源上执行只读参数化查询。
 *
 * <p>执行器通过用户级和数据源级 {@link Semaphore} 同时限制并发，设置查询超时和最大行数，
 * 并跟踪本实例创建的 JDBC 语句以支持取消。并发配额仅在当前应用实例内生效。</p>
 */
@Service
public class JdbcQueryExecutor {
    private final DataSourceRegistry registry;
    private final DialectRegistry dialectRegistry;
    private final QueryProperties properties;
    private final ConcurrentHashMap<Long, Semaphore> userLimits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Semaphore> sourceLimits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Statement> runningStatements = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    /**
     * 注入数据源注册表、方言注册表与查询执行限制配置。
     *
     * @param registry        受管数据源连接池注册表
     * @param dialectRegistry 方言插件注册表
     * @param properties      超时、行数与并发配额配置
     */
    public JdbcQueryExecutor(DataSourceRegistry registry, DialectRegistry dialectRegistry,
                             QueryProperties properties) {
        this.registry = registry;
        this.dialectRegistry = dialectRegistry;
        this.properties = properties;
    }

    /**
     * 获取用户和数据源并发配额后执行查询，并在结束时释放配额。
     * <p>COUNT 时对明细 SQL 做包装；语义查询请传入不含 LIMIT 的 countSql。
     *
     * @param queryId    查询任务标识，用于登记可取消的 JDBC 语句
     * @param userId     发起查询的用户标识
     * @param source     目标数据源
     * @param sql        包含占位符的只读 SQL
     * @param parameters 按占位符顺序绑定的参数
     * @return 列名、结果行、真实总数及是否触顶截断
     */
    public QueryResult execute(String queryId, long userId, DataSourceEntity source,
                               String sql, List<Object> parameters) {
        return execute(queryId, userId, source, sql, parameters, null, null);
    }

    /**
     * 获取用户和数据源并发配额后执行查询；触顶时可使用独立的 COUNT SQL。
     *
     * @param queryId         查询任务标识
     * @param userId          发起查询的用户标识
     * @param source          目标数据源
     * @param sql             明细 SQL
     * @param parameters      明细参数
     * @param countSql        可选 COUNT SQL；为空则对明细 SQL 包装
     * @param countParameters COUNT 参数；为空时复用明细参数
     * @return 查询结果
     */
    public QueryResult execute(String queryId, long userId, DataSourceEntity source,
                               String sql, List<Object> parameters,
                               String countSql, List<Object> countParameters) {
        if (!accepting.get()) {
            throw new BusinessException(503, "查询服务正在关闭");
        }
        AcquiredLimits limits = acquireLimits(userId, source.getId());
        try (var connection = registry.get(source).getConnection()) {
            connection.setReadOnly(true);
            dialectRegistry.resolve(source).prepareConnection(connection, source);
            int maxRows = effectiveMaxRows();
            List<String> columns;
            List<Map<String, Object>> rows;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(effectiveTimeoutSeconds());
                statement.setMaxRows(maxRows);
                statement.setFetchSize(Math.min(500, maxRows));
                for (int index = 0; index < parameters.size(); index++) {
                    statement.setObject(index + 1, parameters.get(index));
                }
                runningStatements.put(queryId, statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    var metadata = resultSet.getMetaData();
                    columns = new ArrayList<>(metadata.getColumnCount());
                    Set<String> usedColumns = new HashSet<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        String baseName = metadata.getColumnLabel(index);
                        String column = baseName;
                        int suffix = 2;
                        while (!usedColumns.add(column)) {
                            column = baseName + "_" + suffix++;
                        }
                        columns.add(column);
                    }
                    rows = new ArrayList<>();
                    while (resultSet.next() && rows.size() < maxRows) {
                        Map<String, Object> row = new LinkedHashMap<>(columns.size());
                        for (int index = 1; index <= columns.size(); index++) {
                            row.put(columns.get(index - 1), resultSet.getObject(index));
                        }
                        rows.add(row);
                    }
                } finally {
                    runningStatements.remove(queryId);
                }
            }
            boolean truncated = rows.size() >= maxRows;
            long total = rows.size();
            if (truncated) {
                total = resolveTotal(connection, queryId, sql, parameters, countSql, countParameters, total);
            }
            return new QueryResult(columns, rows, total, truncated);
        } catch (SQLException exception) {
            throw new BusinessException("查询执行失败：" + exception.getMessage());
        } finally {
            limits.release();
        }
    }

    /**
     * 触顶后解析真实总数；COUNT 失败则降级为已返回行数。
     */
    private long resolveTotal(Connection connection,
                              String queryId,
                              String dataSql,
                              List<Object> dataParameters,
                              String countSql,
                              List<Object> countParameters,
                              long fallback) {
        String sql = countSql == null || countSql.isBlank() ? wrapCountSql(dataSql) : countSql;
        List<Object> bound = countParameters != null ? countParameters : dataParameters;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(effectiveTimeoutSeconds());
            for (int index = 0; index < bound.size(); index++) {
                statement.setObject(index + 1, bound.get(index));
            }
            runningStatements.put(queryId, statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Math.max(fallback, resultSet.getLong(1));
                }
            } finally {
                runningStatements.remove(queryId);
            }
        } catch (SQLException ignored) {
            // 降级：总数至少为已返回行数，truncated 仍为 true
        }
        return fallback;
    }

    /**
     * 将业务 SQL 包装为 COUNT 查询；去掉末尾分号。
     */
    static String wrapCountSql(String sql) {
        String trimmed = sql == null ? "" : sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return "SELECT COUNT(*) FROM (" + trimmed + ") omni_cnt";
    }

    /**
     * 执行只读 SQL 并仅读取结果集元数据（通常配合 {@code LIMIT 0}），用于推断列结构。
     *
     * @param queryId    查询任务标识
     * @param userId     发起用户
     * @param source     目标数据源
     * @param sql        参数化只读 SQL
     * @param parameters 占位符参数
     * @return 按结果集顺序的列元数据
     */
    public List<ColumnMeta> describe(String queryId, long userId, DataSourceEntity source,
                                     String sql, List<Object> parameters) {
        if (!accepting.get()) {
            throw new BusinessException(503, "查询服务正在关闭");
        }
        AcquiredLimits limits = acquireLimits(userId, source.getId());
        try (var connection = registry.get(source).getConnection()) {
            connection.setReadOnly(true);
            dialectRegistry.resolve(source).prepareConnection(connection, source);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(effectiveTimeoutSeconds());
                statement.setMaxRows(1);
                statement.setFetchSize(1);
                for (int index = 0; index < parameters.size(); index++) {
                    statement.setObject(index + 1, parameters.get(index));
                }
                runningStatements.put(queryId, statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return readColumnMetas(resultSet.getMetaData());
                } finally {
                    runningStatements.remove(queryId);
                }
            }
        } catch (SQLException exception) {
            throw new BusinessException("查询执行失败：" + exception.getMessage());
        } finally {
            limits.release();
        }
    }

    /** 配置未绑定或非法时回退，避免 maxRows=0 导致结果恒为空。 */
    private int effectiveMaxRows() {
        return properties.maxRows() > 0 ? properties.maxRows() : 1000;
    }

    private int effectiveTimeoutSeconds() {
        return properties.timeoutSeconds() > 0 ? properties.timeoutSeconds() : 30;
    }

    /**
     * 获取用户与数据源并发配额；配额占满时排队等待，超时则拒绝。
     *
     * @param userId   发起用户
     * @param sourceId 数据源标识
     * @return 已占用的配额，调用方必须 {@link AcquiredLimits#release()}
     */
    private AcquiredLimits acquireLimits(long userId, long sourceId) {
        Semaphore userLimit = userLimits.computeIfAbsent(userId,
                ignored -> new Semaphore(Math.max(1, properties.perUserConcurrency()), false));
        Semaphore sourceLimit = sourceLimits.computeIfAbsent(sourceId,
                ignored -> new Semaphore(Math.max(1, properties.perSourceConcurrency()), false));
        long waitSeconds = Math.max(1L, Math.min(10L, effectiveTimeoutSeconds()));
        boolean userAcquired = false;
        boolean sourceAcquired = false;
        try {
            // 非公平：避免失败请求长时间占着排队位拖死后续看板渲染
            userAcquired = userLimit.tryAcquire(waitSeconds, TimeUnit.SECONDS);
            if (!userAcquired) {
                throw new BusinessException(429, "用户并发查询繁忙，请稍后重试");
            }
            sourceAcquired = sourceLimit.tryAcquire(waitSeconds, TimeUnit.SECONDS);
            if (!sourceAcquired) {
                throw new BusinessException(429, "数据源并发查询繁忙，请稍后重试");
            }
            return new AcquiredLimits(userLimit, sourceLimit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (sourceAcquired) {
                sourceLimit.release();
            }
            if (userAcquired) {
                userLimit.release();
            }
            throw new BusinessException(503, "查询等待被中断");
        } catch (BusinessException exception) {
            if (sourceAcquired) {
                sourceLimit.release();
            }
            if (userAcquired) {
                userLimit.release();
            }
            throw exception;
        }
    }

    /**
     * 已占用的用户/数据源并发许可。
     */
    private record AcquiredLimits(Semaphore userLimit, Semaphore sourceLimit) {
        void release() {
            sourceLimit.release();
            userLimit.release();
        }
    }

    /**
     * 从结果集元数据读取列名与类型；同名列追加后缀去重。
     *
     * @param metadata JDBC 结果集元数据
     * @return 列元数据列表
     */
    private static List<ColumnMeta> readColumnMetas(ResultSetMetaData metadata) throws SQLException {
        List<ColumnMeta> columns = new ArrayList<>(metadata.getColumnCount());
        Set<String> usedColumns = new HashSet<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String baseName = metadata.getColumnLabel(index);
            if (baseName == null || baseName.isBlank()) {
                baseName = metadata.getColumnName(index);
            }
            if (baseName == null || baseName.isBlank()) {
                baseName = "col_" + index;
            }
            String column = baseName;
            int suffix = 2;
            while (!usedColumns.add(column)) {
                column = baseName + "_" + suffix++;
            }
            columns.add(new ColumnMeta(column, metadata.getColumnType(index), metadata.getColumnTypeName(index)));
        }
        return List.copyOf(columns);
    }

    /**
     * 尝试取消本实例中正在执行的 JDBC 语句。
     *
     * @param queryId 查询任务标识
     * @return 找到对应运行中语句并成功发出取消请求时返回 {@code true}，否则返回 {@code false}
     */
    public boolean cancel(String queryId) {
        Statement statement = runningStatements.get(queryId);
        if (statement == null) {
            return false;
        }
        try {
            statement.cancel();
            return true;
        } catch (SQLException exception) {
            throw new BusinessException("取消查询失败：" + exception.getMessage());
        }
    }

    /**
     * 停止接收新查询，并尝试取消本实例中所有正在执行的语句。
     */
    @PreDestroy
    public void close() {
        accepting.set(false);
        runningStatements.values().forEach(statement -> {
            try {
                statement.cancel();
            } catch (SQLException ignored) {
                // 关闭期间继续取消其他查询。
            }
        });
        runningStatements.clear();
    }

    /**
     * JDBC 查询结果。
     *
     * @param columns   按结果集顺序排列且已消除重名的列名
     * @param rows      按列顺序保存键值的结果行（受 max-rows 限制）
     * @param total     真实命中行数（未截断时等于 rows.size；截断时来自 COUNT 或降级值）
     * @param truncated 是否因 max-rows 触顶而可能未拉全明细
     */
    public record QueryResult(List<String> columns, List<Map<String, Object>> rows, long total, boolean truncated) {
        /**
         * 兼容旧调用：总数等于行数，视为未截断。
         */
        public QueryResult(List<String> columns, List<Map<String, Object>> rows) {
            this(columns, rows, rows == null ? 0L : rows.size(), false);
        }
    }

    /**
     * 结果集列元数据。
     *
     * @param name     列标签（已消重）
     * @param jdbcType {@link Types} 常量
     * @param typeName 驱动报告的类型名
     */
    public record ColumnMeta(String name, int jdbcType, String typeName) {
    }
}
