package com.omni.panel.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.common.ClientRequestInfo;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.config.OmniMetrics;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.entity.DataSourceEntity;
import com.omni.panel.entity.DatasetEntity;
import com.omni.panel.entity.DatasetFieldEntity;
import com.omni.panel.entity.MetricEntity;
import com.omni.panel.mapper.DataPolicyMapper;
import com.omni.panel.mapper.DataSourceMapper;
import com.omni.panel.mapper.QueryAuditMapper;
import com.omni.panel.metric.MetricExpression;
import com.omni.panel.query.JdbcQueryExecutor;
import com.omni.panel.query.NamedSqlExpander;
import com.omni.panel.query.QueryCompiler;
import com.omni.panel.query.QueryRequest;
import com.omni.panel.query.QueryStateStore;
import com.omni.panel.query.SqlObjectAccessGuard;
import com.omni.panel.query.SqlPolicyGuard;

/**
 * 统一受理语义查询和原生 SQL 查询，并管理异步执行生命周期。
 *
 * <p>原生 SQL 仅向管理员或具有 {@code query:raw} 权限的用户开放；语义查询在编译前应用
 * 字段和行级数据策略。提交后立即持久化排队状态和审计记录，再由虚拟线程异步执行，
 * 查询结果、失败或取消状态通过状态存储对外提供。</p>
 */
@Service
public class QueryService {
    private final DatasetService datasetService;
    private final DataSourceService dataSourceService;
    private final DataSourceMapper dataSourceMapper;
    private final DataPolicyMapper dataPolicyMapper;
    private final QueryCompiler compiler;
    private final SqlPolicyGuard sqlPolicyGuard;
    private final SqlObjectAccessGuard sqlObjectAccessGuard;
    private final JdbcQueryExecutor executor;
    private final DialectRegistry dialectRegistry;
    private final QueryStateStore stateStore;
    private final QueryAuditMapper auditMapper;
    private final ObjectMapper objectMapper;
    private final MetricService metricService;
    private final OmniMetrics omniMetrics;
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 注入查询编译、策略校验、执行器与审计依赖。
     *
     * @param datasetService       数据集业务服务
     * @param dataSourceService    数据源业务服务
     * @param dataSourceMapper     数据源持久化
     * @param dataPolicyMapper     数据策略持久化
     * @param compiler             语义查询编译器
     * @param sqlPolicyGuard       SQL 策略守卫
     * @param sqlObjectAccessGuard SQL 对象访问守卫
     * @param executor             JDBC 查询执行器
     * @param dialectRegistry      方言注册表
     * @param stateStore           查询状态存储
     * @param auditMapper          查询审计持久化
     * @param objectMapper         JSON 序列化
     * @param metricService        指标业务服务
     * @param omniMetrics          业务指标
     */
    public QueryService(DatasetService datasetService, DataSourceService dataSourceService,
                        DataSourceMapper dataSourceMapper, DataPolicyMapper dataPolicyMapper,
                        QueryCompiler compiler, SqlPolicyGuard sqlPolicyGuard,
                        SqlObjectAccessGuard sqlObjectAccessGuard, JdbcQueryExecutor executor,
                        DialectRegistry dialectRegistry, QueryStateStore stateStore,
                        QueryAuditMapper auditMapper, ObjectMapper objectMapper, MetricService metricService,
                        OmniMetrics omniMetrics) {
        this.datasetService = datasetService;
        this.dataSourceService = dataSourceService;
        this.dataSourceMapper = dataSourceMapper;
        this.dataPolicyMapper = dataPolicyMapper;
        this.compiler = compiler;
        this.sqlPolicyGuard = sqlPolicyGuard;
        this.sqlObjectAccessGuard = sqlObjectAccessGuard;
        this.executor = executor;
        this.dialectRegistry = dialectRegistry;
        this.stateStore = stateStore;
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
        this.metricService = metricService;
        this.omniMetrics = omniMetrics;
    }

    /**
     * 校验并异步提交查询（无客户端信息时使用）。
     *
     * @param submission 原生 SQL 或语义查询提交内容
     * @return 可用于查询状态和发起取消的查询任务标识
     */
    public String submit(QuerySubmission submission) {
        return submit(submission, null);
    }

    /**
     * 校验并异步提交查询，同时创建初始状态和审计记录。
     *
     * @param submission 原生 SQL 或语义查询提交内容
     * @param client     客户端 IP 与浏览器信息
     * @return 可用于查询状态和发起取消的查询任务标识
     */
    public String submit(QuerySubmission submission, ClientRequestInfo.Info client) {
        AuthenticatedUser user = AuthenticatedUser.current();
        if (!user.admin() && !user.permissions().contains("query:execute")) {
            throw new BusinessException(403, "缺少查询执行权限");
        }
        Execution execution = submission.query() == null
                ? rawExecution(submission, user)
                : semanticExecution(submission.query(), user);
        sqlPolicyGuard.validate(execution.sql(), dialectRegistry.resolve(execution.source()));
        String queryId = UUID.randomUUID().toString();
        long startedAtMs = System.currentTimeMillis();
        ClientRequestInfo.Info clientInfo = client == null ? new ClientRequestInfo.Info(null, null) : client;
        stateStore.save(snapshot(queryId, user.id(), execution.source().getId(), "QUEUED", null, null, startedAtMs));
        auditMapper.start(queryId, user.id(), execution.source().getId(), execution.sql(),
                clientInfo.clientIp(), clientInfo.userAgent());
        omniMetrics.querySubmit();
        virtualExecutor.submit(() -> run(queryId, user.id(), execution, startedAtMs));
        return queryId;
    }

    /**
     * 构建原生 SQL 查询的执行上下文，并校验用户具备 raw 权限。
     *
     * @param submission 包含数据源和 SQL 的提交内容
     * @param user       当前认证用户
     * @return 待执行的数据源、SQL 和参数
     */
    private Execution rawExecution(QuerySubmission submission, AuthenticatedUser user) {
        if (submission.sourceId() == null || submission.sql() == null) {
            throw new BusinessException("SQL 查询需要数据源和 SQL");
        }
        if (!user.admin() && !user.permissions().contains("query:raw")) {
            throw new BusinessException(403, "缺少原生 SQL 查询权限");
        }
        DataSourceEntity source = dataSourceService.require(submission.sourceId(), "READ");
        NamedSqlExpander.Expanded expanded = NamedSqlExpander.expand(
                submission.sql(),
                submission.parameters(),
                submission.namedParameters());
        sqlObjectAccessGuard.validateForCurrentUser(
                source.getId(), expanded.sql(), source.getDefaultDatabase());
        return new Execution(source, expanded.sql(), expanded.parameters(), null, null);
    }

    /**
     * 加载数据集元数据、应用字段与行级策略后编译语义查询。
     *
     * @param request 语义查询请求
     * @param user    当前认证用户
     * @return 编译后的执行上下文
     */
    private Execution semanticExecution(QueryRequest request, AuthenticatedUser user) {
        DatasetEntity dataset = datasetService.require(request.datasetId(), "READ");
        DataSourceEntity source = dataSourceService.require(dataset.getDataSourceId(), "READ");
        List<DatasetFieldEntity> fields = datasetService.fields(dataset.getId());
        Set<String> denied = deniedFields(dataset.getId(), user, fields);
        List<QueryCompiler.FieldDefinition> fieldDefs = new ArrayList<>(fields.stream()
                .map(field -> new QueryCompiler.FieldDefinition(
                        field.getName(), field.getColumnName(), field.getFieldType(), field.getAggregation()))
                .toList());
        List<String> metricNames = new ArrayList<>();
        if (request.metrics() != null) {
            metricNames.addAll(request.metrics());
        }
        expandBusinessMetrics(request, dataset.getId(), fields, denied, fieldDefs, metricNames);
        QueryRequest expanded = new QueryRequest(
                request.datasetId(),
                request.dimensions(),
                List.copyOf(metricNames),
                null,
                request.filter(),
                request.sorts(),
                request.limit());
        var definition = new QueryCompiler.DatasetDefinition(dataset.getSchemaName(), dataset.getTableName(),
                dataset.getDefinitionSql(), List.copyOf(fieldDefs));
        List<QueryRequest.FilterNode> rowRules = user.admin() ? List.of()
                : dataPolicyMapper.rowRules(dataset.getId(), user.id()).stream().map(this::parseRule).toList();
        QueryCompiler.CompiledQuery compiled = compiler.compile(
                expanded, definition, denied, rowRules, dialectRegistry.resolve(source));
        return new Execution(source, compiled.sql(), compiled.parameters(),
                compiled.countSql(), compiled.countParameters());
    }

    /**
     * 将 bi_metric 展开为可编译的虚拟指标字段，并追加到 metrics 列表。
     *
     * @param request     语义查询请求
     * @param datasetId   数据集标识
     * @param fields      数据集字段列表
     * @param denied      被拒绝访问的字段名集合
     * @param fieldDefs   可变的编译字段定义列表
     * @param metricNames 可变的指标名列表
     */
    private void expandBusinessMetrics(QueryRequest request, long datasetId, List<DatasetFieldEntity> fields,
                                       Set<String> denied, List<QueryCompiler.FieldDefinition> fieldDefs,
                                       List<String> metricNames) {
        if (request.metricIds() == null || request.metricIds().isEmpty()) {
            return;
        }
        Map<String, DatasetFieldEntity> byName = new HashMap<>();
        for (DatasetFieldEntity field : fields) {
            byName.put(field.getName(), field);
        }
        Set<String> usedNames = new HashSet<>();
        fieldDefs.forEach(field -> usedNames.add(field.name()));
        usedNames.addAll(metricNames);
        for (Long metricId : request.metricIds()) {
            if (metricId == null) {
                continue;
            }
            MetricEntity metric = metricService.require(metricId, "READ");
            if (metric.getModelId() == null || metric.getModelId() != datasetId) {
                throw new BusinessException("指标不属于当前模型：" + metric.getName());
            }
            String fieldRef = MetricExpression.requireFieldName(metric.getExpressionJson());
            if (denied.contains(fieldRef)) {
                throw new BusinessException("字段不在允许的元数据白名单中：" + fieldRef);
            }
            DatasetFieldEntity base = byName.get(fieldRef);
            if (base == null) {
                throw new BusinessException("指标引用的模型字段不存在：" + fieldRef);
            }
            String semanticName = metric.getName();
            if (usedNames.contains(semanticName)) {
                semanticName = metric.getName() + "_" + metric.getId();
            }
            usedNames.add(semanticName);
            fieldDefs.add(new QueryCompiler.FieldDefinition(
                    semanticName, base.getColumnName(), "METRIC", metric.getAggregation()));
            metricNames.add(semanticName);
        }
    }

    /**
     * 计算当前用户在指定数据集上不可访问的字段名集合。
     *
     * @param datasetId 数据集标识
     * @param user      当前认证用户
     * @param fields    数据集全部字段
     * @return 被拒绝访问的字段名；管理员或无策略时为空集
     */
    private Set<String> deniedFields(long datasetId, AuthenticatedUser user, List<DatasetFieldEntity> fields) {
        if (user.admin() || dataPolicyMapper.fieldRuleCount(datasetId, user.id()) == 0) {
            return Set.of();
        }
        Set<String> allowed = Set.copyOf(dataPolicyMapper.allowedFields(datasetId, user.id()));
        Set<String> denied = new HashSet<>();
        fields.stream().map(DatasetFieldEntity::getName)
                .filter(field -> !allowed.contains(field)).forEach(denied::add);
        return Set.copyOf(denied);
    }

    /**
     * 将 JSON 字符串反序列化为行级权限过滤节点。
     *
     * @param json 行级规则 JSON
     * @return 过滤条件节点
     */
    private QueryRequest.FilterNode parseRule(String json) {
        try {
            return objectMapper.readValue(json, QueryRequest.FilterNode.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "行级权限规则配置错误");
        }
    }

    /**
     * 在虚拟线程中执行查询，更新状态存储并完成审计记录。
     *
     * @param queryId     查询任务标识
     * @param userId      发起用户标识
     * @param execution   待执行的 SQL 上下文
     * @param startedAtMs 提交时间毫秒戳
     */
    private void run(String queryId, long userId, Execution execution, long startedAtMs) {
        QueryStateStore.QuerySnapshot queued = stateStore.get(queryId);
        if (queued != null && "CANCELLED".equals(queued.status())) {
            finishAudit(queryId, "CANCELLED", null, null, startedAtMs);
            stateStore.save(snapshot(queryId, userId, execution.source().getId(), "CANCELLED", null, null, startedAtMs));
            return;
        }
        stateStore.save(snapshot(queryId, userId, execution.source().getId(), "RUNNING", null, null, startedAtMs));
        try {
            JdbcQueryExecutor.QueryResult result = executor.execute(
                    queryId, userId, execution.source(), execution.sql(), execution.parameters(),
                    execution.countSql(), execution.countParameters());
            QueryStateStore.QuerySnapshot current = stateStore.get(queryId);
            if (current != null && "CANCELLED".equals(current.status())) {
                finishAudit(queryId, "CANCELLED", null, null, startedAtMs);
                stateStore.save(snapshot(queryId, userId, execution.source().getId(), "CANCELLED", null, null, startedAtMs));
                return;
            }
            stateStore.save(snapshot(queryId, userId, execution.source().getId(), "SUCCEEDED", result, null, startedAtMs));
            finishAudit(queryId, "SUCCEEDED", result, null, startedAtMs);
        } catch (Exception exception) {
            QueryStateStore.QuerySnapshot current = stateStore.get(queryId);
            String status = current != null && "CANCELLED".equals(current.status()) ? "CANCELLED" : "FAILED";
            String error = "FAILED".equals(status) ? exception.getMessage() : null;
            stateStore.save(snapshot(queryId, userId, execution.source().getId(), status, null, error, startedAtMs));
            finishAudit(queryId, status, null, error, startedAtMs);
        }
    }

    /**
     * 写入查询结束时的审计信息，包括耗时、行数和结果预览。
     *
     * @param queryId     查询任务标识
     * @param status      最终状态
     * @param result      成功时的查询结果，可为 null
     * @param error       失败时的错误信息，可为 null
     * @param startedAtMs 提交时间毫秒戳
     */
    private void finishAudit(String queryId, String status, JdbcQueryExecutor.QueryResult result,
                             String error, long startedAtMs) {
        long durationMs = Math.max(0L, System.currentTimeMillis() - startedAtMs);
        Integer rowCount = result == null ? null : result.rows().size();
        String preview = "SUCCEEDED".equals(status) ? QueryAuditPreview.build(objectMapper, result) : null;
        auditMapper.finish(queryId, status, rowCount, error, durationMs, preview);
        omniMetrics.queryComplete(status, startedAtMs);
    }

    /**
     * 获取当前用户可访问的查询任务快照。
     *
     * @param queryId 查询任务标识
     * @return 查询任务的当前状态、结果或错误信息
     */
    public QueryStateStore.QuerySnapshot get(String queryId) {
        QueryStateStore.QuerySnapshot snapshot = requireOwned(queryId);
        return snapshot;
    }

    /**
     * 将未结束的查询标记为已取消，并尝试取消本实例正在执行的 JDBC 语句。
     *
     * <p>状态可跨实例读取，但底层 JDBC 取消仅对持有该语句的当前应用实例有效。</p>
     *
     * @param queryId 查询任务标识
     */
    public void cancel(String queryId) {
        QueryStateStore.QuerySnapshot snapshot = requireOwned(queryId);
        if (Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(snapshot.status())) {
            throw new BusinessException("查询已结束");
        }
        long startedAtMs = snapshot.startedAtMs() == null ? System.currentTimeMillis() : snapshot.startedAtMs();
        stateStore.save(snapshot(queryId, snapshot.userId(), snapshot.sourceId(), "CANCELLED", null, null, startedAtMs));
        executor.cancel(queryId);
    }

    /**
     * 读取查询快照并校验当前用户有权访问。
     *
     * @param queryId 查询任务标识
     * @return 通过权限校验的查询快照
     */
    private QueryStateStore.QuerySnapshot requireOwned(String queryId) {
        QueryStateStore.QuerySnapshot snapshot = stateStore.get(queryId);
        AuthenticatedUser user = AuthenticatedUser.current();
        if (snapshot == null) {
            throw new BusinessException(404, "查询任务不存在");
        }
        if (!user.admin() && snapshot.userId() != user.id()) {
            throw new BusinessException(403, "无权访问该查询任务");
        }
        return snapshot;
    }

    /**
     * 构造查询状态快照，终态时自动计算耗时。
     *
     * @param queryId     查询任务标识
     * @param userId      发起用户标识
     * @param sourceId    数据源标识
     * @param status      任务状态
     * @param result      成功时的查询结果，可为 null
     * @param error       失败时的错误信息，可为 null
     * @param startedAtMs 提交时间毫秒戳
     * @return 查询状态快照
     */
    private static QueryStateStore.QuerySnapshot snapshot(String queryId, long userId, long sourceId, String status,
                                                          JdbcQueryExecutor.QueryResult result, String error,
                                                          long startedAtMs) {
        Long durationMs = null;
        if (Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(status)) {
            durationMs = Math.max(0L, System.currentTimeMillis() - startedAtMs);
        }
        return new QueryStateStore.QuerySnapshot(
                queryId, userId, sourceId, status, result, error, startedAtMs, durationMs);
    }

    /**
     * 停止接收和执行查询，并取消本实例仍在运行的 JDBC 语句。
     */
    @PreDestroy
    public void close() {
        virtualExecutor.shutdownNow();
        executor.close();
        virtualExecutor.close();
    }

    /**
     * 查询提交内容；{@code query} 为空时按原生 SQL 查询处理。
     *
     * @param sourceId        原生 SQL 使用的数据源标识
     * @param sql             原生 SQL 文本（可含 {@code :name} 或 {@code ?}）
     * @param parameters      裸 {@code ?} 的顺序参数
     * @param namedParameters {@code :name} 命名参数；可为 {@code null}
     * @param query           语义查询请求
     */
    public record QuerySubmission(Long sourceId, String sql, List<Object> parameters,
                                  Map<String, Object> namedParameters, QueryRequest query) {
        /**
         * 兼容旧调用：无命名参数。
         */
        public QuerySubmission(Long sourceId, String sql, List<Object> parameters, QueryRequest query) {
            this(sourceId, sql, parameters, null, query);
        }
    }

    /**
     * 查询执行的内部上下文：目标数据源、SQL 及参数。
     *
     * @param source          目标数据源
     * @param sql             待执行 SQL
     * @param parameters      占位符参数
     * @param countSql        可选 COUNT SQL（语义查询不含 LIMIT）
     * @param countParameters COUNT 参数
     */
    private record Execution(DataSourceEntity source, String sql, List<Object> parameters,
                             String countSql, List<Object> countParameters) {
    }
}
