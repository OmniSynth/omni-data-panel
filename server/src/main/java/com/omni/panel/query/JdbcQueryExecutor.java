package com.omni.panel.query;

import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.DataSourceEntity;
import com.omni.panel.datasource.DataSourceRegistry;
import com.omni.panel.datasource.dialect.DialectRegistry;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

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

    public JdbcQueryExecutor(DataSourceRegistry registry, DialectRegistry dialectRegistry,
                             QueryProperties properties) {
        this.registry = registry;
        this.dialectRegistry = dialectRegistry;
        this.properties = properties;
    }

    /**
     * 获取用户和数据源并发配额后执行查询，并在结束时释放配额。
     *
     * @param queryId 查询任务标识，用于登记可取消的 JDBC 语句
     * @param userId 发起查询的用户标识
     * @param source 目标数据源
     * @param sql 包含占位符的只读 SQL
     * @param parameters 按占位符顺序绑定的参数
     * @return 保持 JDBC 列顺序的列名和结果行
     */
    public QueryResult execute(String queryId, long userId, DataSourceEntity source,
                               String sql, List<Object> parameters) {
        if (!accepting.get()) {
            throw new BusinessException(503, "查询服务正在关闭");
        }
        Semaphore userLimit = userLimits.computeIfAbsent(userId,
            ignored -> new Semaphore(properties.perUserConcurrency()));
        Semaphore sourceLimit = sourceLimits.computeIfAbsent(source.getId(),
            ignored -> new Semaphore(properties.perSourceConcurrency()));
        if (!userLimit.tryAcquire()) {
            throw new BusinessException(429, "用户并发查询数已达上限");
        }
        if (!sourceLimit.tryAcquire()) {
            userLimit.release();
            throw new BusinessException(429, "数据源并发查询数已达上限");
        }
        try (var connection = registry.get(source).getConnection()) {
            connection.setReadOnly(true);
            dialectRegistry.resolve(source).prepareConnection(connection, source);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(properties.timeoutSeconds());
                statement.setMaxRows(properties.maxRows());
                statement.setFetchSize(500);
                for (int index = 0; index < parameters.size(); index++) {
                    statement.setObject(index + 1, parameters.get(index));
                }
                runningStatements.put(queryId, statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    var metadata = resultSet.getMetaData();
                    List<String> columns = new ArrayList<>(metadata.getColumnCount());
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
                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (resultSet.next() && rows.size() < properties.maxRows()) {
                        Map<String, Object> row = new LinkedHashMap<>(columns.size());
                        for (int index = 1; index <= columns.size(); index++) {
                            row.put(columns.get(index - 1), resultSet.getObject(index));
                        }
                        rows.add(row);
                    }
                    return new QueryResult(columns, rows);
                } finally {
                    runningStatements.remove(queryId);
                }
            }
        } catch (SQLException exception) {
            throw new BusinessException("查询执行失败：" + exception.getMessage());
        } finally {
            sourceLimit.release();
            userLimit.release();
        }
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
     * @param columns 按结果集顺序排列且已消除重名的列名
     * @param rows 按列顺序保存键值的结果行
     */
    public record QueryResult(List<String> columns, List<Map<String, Object>> rows) {}
}
