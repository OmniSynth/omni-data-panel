package com.omni.panel.service;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.datasource.dialect.DialectRegistry;
import com.omni.panel.entity.DataSourceEntity;
import com.omni.panel.entity.DatasetEntity;
import com.omni.panel.entity.DatasetFieldEntity;
import com.omni.panel.mapper.DataPolicyMapper;
import com.omni.panel.mapper.DatasetFieldMapper;
import com.omni.panel.mapper.DatasetMapper;
import com.omni.panel.mapper.MetadataMapper;
import com.omni.panel.query.JdbcQueryExecutor;
import com.omni.panel.query.QueryCompiler;
import com.omni.panel.query.SqlObjectAccessGuard;
import com.omni.panel.query.SqlPolicyGuard;

/**
 * 管理模型（数据集）及字段定义，支持表模型与 SQL 模型。
 */
@Service
public class DatasetService {
    private static final Set<String> AGGREGATIONS = Set.of("SUM", "COUNT", "AVG", "MIN", "MAX");
    private static final Set<String> MODEL_TYPES = Set.of("TABLE", "SQL");
    private static final int DISTINCT_DEFAULT_LIMIT = 200;
    private static final int DISTINCT_MAX_LIMIT = 2000;

    private final DatasetMapper datasetMapper;
    private final DatasetFieldMapper fieldMapper;
    private final MetadataMapper metadataMapper;
    private final DataSourceService dataSourceService;
    private final PermissionService permissionService;
    private final SqlPolicyGuard sqlPolicyGuard;
    private final CollectionService collectionService;
    private final QueryCompiler queryCompiler;
    private final DialectRegistry dialectRegistry;
    private final JdbcQueryExecutor jdbcQueryExecutor;
    private final DataPolicyMapper dataPolicyMapper;
    private final DataSourceObjectAclService objectAclService;
    private final SqlObjectAccessGuard sqlObjectAccessGuard;
    private final DatasetAuditService datasetAuditService;

    public DatasetService(DatasetMapper datasetMapper, DatasetFieldMapper fieldMapper, MetadataMapper metadataMapper,
                          DataSourceService dataSourceService, PermissionService permissionService,
                          SqlPolicyGuard sqlPolicyGuard, @Lazy CollectionService collectionService,
                          QueryCompiler queryCompiler, DialectRegistry dialectRegistry,
                          JdbcQueryExecutor jdbcQueryExecutor, DataPolicyMapper dataPolicyMapper,
                          DataSourceObjectAclService objectAclService, SqlObjectAccessGuard sqlObjectAccessGuard,
                          DatasetAuditService datasetAuditService) {
        this.datasetMapper = datasetMapper;
        this.fieldMapper = fieldMapper;
        this.metadataMapper = metadataMapper;
        this.dataSourceService = dataSourceService;
        this.permissionService = permissionService;
        this.sqlPolicyGuard = sqlPolicyGuard;
        this.collectionService = collectionService;
        this.queryCompiler = queryCompiler;
        this.dialectRegistry = dialectRegistry;
        this.jdbcQueryExecutor = jdbcQueryExecutor;
        this.dataPolicyMapper = dataPolicyMapper;
        this.objectAclService = objectAclService;
        this.sqlObjectAccessGuard = sqlObjectAccessGuard;
        this.datasetAuditService = datasetAuditService;
    }

    /**
     * 查询当前用户可读取且未删除的模型。
     *
     * @return 可读模型列表
     */
    public List<DatasetEntity> listReadable() {
        return datasetMapper.selectList(Wrappers.<DatasetEntity>lambdaQuery()
                        .isNull(DatasetEntity::getDeletedAt))
                .stream()
                .filter(dataset -> permissionService.canRead("DATASET", dataset.getId(), dataset.getOwnerId()))
                .toList();
    }

    /**
     * 查询当前用户可管理的已软删模型。
     *
     * @return 废纸篓中的模型
     */
    public List<DatasetEntity> listTrash() {
        AuthenticatedUser user = AuthenticatedUser.current();
        return datasetMapper.selectList(Wrappers.<DatasetEntity>lambdaQuery()
                        .isNotNull(DatasetEntity::getDeletedAt)
                        .orderByDesc(DatasetEntity::getDeletedAt))
                .stream()
                .filter(dataset -> user.admin() || dataset.getOwnerId().equals(user.id()))
                .toList();
    }

    /**
     * 获取模型并校验当前用户具备指定权限。
     *
     * @param id         模型标识
     * @param permission 所需权限
     * @return 已通过权限校验的模型
     */
    public DatasetEntity require(long id, String permission) {
        DatasetEntity dataset = datasetMapper.selectById(id);
        if (dataset == null || dataset.getDeletedAt() != null) {
            throw new BusinessException(404, "模型不存在");
        }
        permissionService.require("DATASET", id, dataset.getOwnerId(), permission);
        return dataset;
    }

    /**
     * 获取已软删模型并校验写权限（管理员可跨用户）。
     *
     * @param id 模型标识
     * @return 已软删模型
     */
    public DatasetEntity requireTrashed(long id) {
        DatasetEntity dataset = datasetMapper.selectById(id);
        AuthenticatedUser user = AuthenticatedUser.current();
        if (dataset == null || dataset.getDeletedAt() == null) {
            throw new BusinessException(404, "废纸篓中不存在该模型");
        }
        if (!user.admin() && !dataset.getOwnerId().equals(user.id())) {
            throw new BusinessException(403, "无权操作该模型");
        }
        return dataset;
    }

    /**
     * 根据定义 SQL 探测结果列，推断模型输出字段（维度/指标）。
     *
     * @param dataSourceId 数据源标识
     * @param sql          模型定义 SQL
     * @return 推断出的字段列表
     */
    public List<InferredField> inferSqlFields(long dataSourceId, String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("SQL 不能为空");
        }
        DataSourceEntity source = dataSourceService.require(dataSourceId, "READ");
        var dialect = dialectRegistry.resolve(source);
        String trimmed = sql.trim().replaceAll(";\\s*$", "");
        sqlPolicyGuard.validate(trimmed, dialect);
        String probeSql = "SELECT * FROM (" + trimmed + ") AS " + dialect.quoteIdentifier("_probe")
                + " " + dialect.limitPlaceholder();
        AuthenticatedUser user = AuthenticatedUser.current();
        List<JdbcQueryExecutor.ColumnMeta> columns = jdbcQueryExecutor.describe(
                "infer-" + UUID.randomUUID(), user.id(), source, probeSql, List.of(0));
        if (columns.isEmpty()) {
            throw new BusinessException("未能从 SQL 结果中识别任何字段");
        }
        List<InferredField> fields = new ArrayList<>(columns.size());
        for (JdbcQueryExecutor.ColumnMeta column : columns) {
            boolean metric = isNumericJdbcType(column.jdbcType(), column.typeName());
            fields.add(new InferredField(
                    column.name(),
                    column.name(),
                    metric ? "METRIC" : "DIMENSION",
                    metric ? "SUM" : null,
                    column.typeName()));
        }
        return List.copyOf(fields);
    }

    /**
     * 根据 JDBC 类型与类型名判断列是否按指标（数值）推断。
     *
     * @param jdbcType JDBC {@link Types} 常量
     * @param typeName 驱动返回的类型名
     * @return 数值类型时返回 {@code true}
     */
    private static boolean isNumericJdbcType(int jdbcType, String typeName) {
        return switch (jdbcType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                 Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> true;
            default -> {
                String normalized = typeName == null ? "" : typeName.toUpperCase(Locale.ROOT);
                yield normalized.contains("INT")
                        || normalized.contains("DECIMAL")
                        || normalized.contains("NUMERIC")
                        || normalized.contains("FLOAT")
                        || normalized.contains("DOUBLE")
                        || normalized.contains("REAL")
                        || normalized.contains("MONEY");
            }
        };
    }

    /**
     * SQL 推断出的模型字段。
     *
     * @param name       语义名称（默认等于列名）
     * @param columnName SQL 结果列名
     * @param fieldType  DIMENSION 或 METRIC
     * @param aggregation 指标默认聚合，维度为 null
     * @param jdbcTypeName 驱动类型名（展示用）
     */
    public record InferredField(String name, String columnName, String fieldType,
                                String aggregation, String jdbcTypeName) {
    }

    /**
     * 查询模型字段定义。
     *
     * @param datasetId 模型标识
     * @return 字段列表
     */
    public List<DatasetFieldEntity> fields(long datasetId) {
        require(datasetId, "READ");
        return fieldMapper.selectList(Wrappers.<DatasetFieldEntity>lambdaQuery()
                .eq(DatasetFieldEntity::getDatasetId, datasetId).orderByAsc(DatasetFieldEntity::getId));
    }

    /**
     * 查询模型字段去重取值（只读），供仪表盘参数动态选项使用。
     *
     * @param datasetId 模型标识
     * @param fieldName 语义字段名
     * @param limit     最大条数，默认 200，上限 2000
     * @return 去重后的取值列表（保持查询顺序）
     */
    public List<Object> listDistinctValues(long datasetId, String fieldName, Integer limit) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new BusinessException("字段名不能为空");
        }
        DatasetEntity dataset = require(datasetId, "READ");
        List<DatasetFieldEntity> fields = fieldMapper.selectList(Wrappers.<DatasetFieldEntity>lambdaQuery()
                .eq(DatasetFieldEntity::getDatasetId, datasetId).orderByAsc(DatasetFieldEntity::getId));
        AuthenticatedUser user = AuthenticatedUser.current();
        Set<String> denied = deniedFields(datasetId, user, fields);
        DataSourceEntity source = dataSourceService.require(dataset.getDataSourceId(), "READ");
        int capped = limit == null ? DISTINCT_DEFAULT_LIMIT : Math.min(Math.max(limit, 1), DISTINCT_MAX_LIMIT);
        var definition = new QueryCompiler.DatasetDefinition(dataset.getSchemaName(), dataset.getTableName(),
                dataset.getDefinitionSql(),
                fields.stream().map(field -> new QueryCompiler.FieldDefinition(
                        field.getName(), field.getColumnName(), field.getFieldType(), field.getAggregation())).toList());
        QueryCompiler.CompiledQuery compiled = queryCompiler.compileDistinct(
                definition, fieldName.trim(), capped, denied, dialectRegistry.resolve(source));
        if (dataset.getDefinitionSql() != null && !dataset.getDefinitionSql().isBlank()) {
            sqlPolicyGuard.validate(dataset.getDefinitionSql(), dialectRegistry.resolve(source));
        }
        JdbcQueryExecutor.QueryResult result = jdbcQueryExecutor.execute(
                "distinct-" + UUID.randomUUID(), user.id(), source, compiled.sql(), compiled.parameters());
        List<Object> values = new ArrayList<>(result.rows().size());
        String column = result.columns().isEmpty() ? fieldName.trim() : result.columns().getFirst();
        for (var row : result.rows()) {
            Object value = row.get(column);
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    /**
     * 计算当前用户在该模型上被字段策略拒绝的字段名集合。
     *
     * @param datasetId 模型标识
     * @param user      当前用户
     * @param fields    模型字段
     * @return 拒绝字段名；管理员或无策略时为空集
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
     * 创建模型。
     *
     * @param input 创建参数
     * @return 已创建模型
     */
    @Transactional
    public DatasetEntity create(SaveInput input) {
        AuthenticatedUser user = AuthenticatedUser.current();
        long collectionId = resolveCollectionId(input.collectionId(), user.id());
        String modelType = normalizeModelType(input.modelType());
        dataSourceService.require(input.dataSourceId(), "READ");
        DatasetEntity dataset = new DatasetEntity();
        dataset.setName(input.name());
        dataset.setDescription(input.description());
        dataset.setModelType(modelType);
        dataset.setDataSourceId(input.dataSourceId());
        dataset.setOwnerId(user.id());
        dataset.setCollectionId(collectionId);
        dataset.setCreatedAt(LocalDateTime.now());
        dataset.setUpdatedAt(dataset.getCreatedAt());
        applyModelFields(dataset, modelType, input);
        datasetMapper.insert(dataset);
        saveFields(dataset, input.fields(), modelType);
        datasetAuditService.record(dataset, "CREATE", summary(dataset));
        return dataset;
    }

    /**
     * 更新模型并以新字段定义整体替换。
     *
     * @param id    模型标识
     * @param input 更新参数
     * @return 已更新模型
     */
    @Transactional
    public DatasetEntity update(long id, SaveInput input) {
        DatasetEntity dataset = require(id, "WRITE");
        String modelType = normalizeModelType(input.modelType());
        dataSourceService.require(input.dataSourceId(), "READ");
        if (input.collectionId() != null) {
            collectionService.requireReadable(input.collectionId());
            dataset.setCollectionId(input.collectionId());
        }
        dataset.setName(input.name());
        dataset.setDescription(input.description());
        dataset.setModelType(modelType);
        dataset.setDataSourceId(input.dataSourceId());
        dataset.setUpdatedAt(LocalDateTime.now());
        applyModelFields(dataset, modelType, input);
        fieldMapper.delete(Wrappers.<DatasetFieldEntity>lambdaQuery()
                .eq(DatasetFieldEntity::getDatasetId, id));
        saveFields(dataset, input.fields(), modelType);
        datasetMapper.updateById(dataset);
        datasetAuditService.record(dataset, "UPDATE", summary(dataset));
        return dataset;
    }

    /**
     * 软删除模型。
     *
     * @param id 模型标识
     */
    @Transactional
    public void softDelete(long id) {
        DatasetEntity dataset = require(id, "WRITE");
        dataset.setDeletedAt(LocalDateTime.now());
        dataset.setUpdatedAt(dataset.getDeletedAt());
        datasetMapper.updateById(dataset);
        datasetAuditService.record(dataset, "SOFT_DELETE", summary(dataset));
    }

    /**
     * 从废纸篓恢复模型。
     *
     * @param id 模型标识
     */
    @Transactional
    public void restore(long id) {
        DatasetEntity dataset = requireTrashed(id);
        dataset.setDeletedAt(null);
        dataset.setUpdatedAt(LocalDateTime.now());
        datasetMapper.updateById(dataset);
        datasetAuditService.record(dataset, "RESTORE", summary(dataset));
    }

    /**
     * 永久删除模型。
     *
     * @param id 模型标识
     */
    @Transactional
    public void purge(long id) {
        DatasetEntity dataset = requireTrashed(id);
        String detail = summary(dataset);
        permissionService.deleteResource("DATASET", id);
        datasetMapper.deleteById(dataset.getId());
        datasetAuditService.record(dataset, "PURGE", detail);
    }

    private static String summary(DatasetEntity dataset) {
        return "modelType=" + dataset.getModelType()
                + ", dataSourceId=" + dataset.getDataSourceId()
                + ", collectionId=" + dataset.getCollectionId();
    }

    /**
     * 解析目标集合标识；未指定时使用用户个人根集合。
     *
     * @param collectionId 可选集合标识
     * @param userId       用户标识
     * @return 有效集合标识
     */
    private long resolveCollectionId(Long collectionId, long userId) {
        if (collectionId != null) {
            collectionService.requireReadable(collectionId);
            return collectionId;
        }
        return collectionService.ensurePersonalCollection(userId).getId();
    }

    /**
     * 规范化模型类型编码，空值默认为 TABLE。
     *
     * @param modelType 模型类型
     * @return 大写的 TABLE 或 SQL
     */
    private String normalizeModelType(String modelType) {
        String normalized = modelType == null || modelType.isBlank() ? "TABLE" : modelType.toUpperCase();
        if (!MODEL_TYPES.contains(normalized)) {
            throw new BusinessException("模型类型仅支持 TABLE 或 SQL");
        }
        return normalized;
    }

    /**
     * 按模型类型写入表引用或 SQL 定义字段。
     *
     * @param dataset   目标模型实体
     * @param modelType 模型类型 TABLE 或 SQL
     * @param input     保存参数
     */
    private void applyModelFields(DatasetEntity dataset, String modelType, SaveInput input) {
        if ("SQL".equals(modelType)) {
            if (input.definitionSql() == null || input.definitionSql().isBlank()) {
                throw new BusinessException("SQL 模型必须提供 definitionSql");
            }
            sqlPolicyGuard.validate(input.definitionSql());
            DataSourceEntity source = dataSourceService.require(dataset.getDataSourceId(), "READ");
            sqlObjectAccessGuard.validateForCurrentUser(
                    source.getId(), input.definitionSql(), source.getDefaultDatabase());
            dataset.setDefinitionSql(input.definitionSql());
            dataset.setSchemaName(blankToNull(input.schemaName()));
            dataset.setTableName(blankToNull(input.tableName()));
            return;
        }
        if (input.schemaName() == null || input.schemaName().isBlank()
                || input.tableName() == null || input.tableName().isBlank()) {
            throw new BusinessException("表模型必须提供 schemaName 与 tableName");
        }
        objectAclService.requireTableAllowed(dataset.getDataSourceId(), input.schemaName(), input.tableName());
        dataset.setSchemaName(input.schemaName());
        dataset.setTableName(input.tableName());
        dataset.setDefinitionSql(null);
    }

    /**
     * 将空白字符串转为 {@code null}。
     *
     * @param value 原始字符串
     * @return 非空白 trimmed 值，或 {@code null}
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 校验并持久化模型字段定义；表模型需与已同步元数据一致。
     *
     * @param dataset   已插入的模型实体
     * @param fields    字段输入列表
     * @param modelType 模型类型
     */
    private void saveFields(DatasetEntity dataset, List<FieldInput> fields, String modelType) {
        if (fields == null || fields.isEmpty()) {
            throw new BusinessException("模型至少需要一个字段");
        }
        if ("TABLE".equals(modelType)) {
            if (metadataMapper.tableExists(dataset.getDataSourceId(), dataset.getSchemaName(),
                    dataset.getTableName()) == 0) {
                throw new BusinessException("数据表不在已同步元数据中");
            }
        }
        for (FieldInput input : fields) {
            if ("TABLE".equals(modelType)
                    && metadataMapper.columnExists(dataset.getDataSourceId(), dataset.getSchemaName(),
                    dataset.getTableName(), input.columnName()) == 0) {
                throw new BusinessException("字段不在已同步元数据中：" + input.columnName());
            }
            if ("TABLE".equals(modelType)) {
                objectAclService.requireColumnAllowed(
                        dataset.getDataSourceId(), dataset.getSchemaName(), dataset.getTableName(), input.columnName());
            }
            String type = input.fieldType().toUpperCase();
            String aggregation = input.aggregation() == null ? null : input.aggregation().toUpperCase();
            if (!Set.of("DIMENSION", "METRIC").contains(type)
                    || ("METRIC".equals(type) && !AGGREGATIONS.contains(aggregation))) {
                throw new BusinessException("字段类型或聚合方式不合法");
            }
            DatasetFieldEntity field = new DatasetFieldEntity();
            field.setDatasetId(dataset.getId());
            field.setName(input.name());
            field.setColumnName(input.columnName());
            field.setFieldType(type);
            field.setAggregation(aggregation);
            fieldMapper.insert(field);
        }
    }

    /**
     * 模型保存输入。
     */
    public record SaveInput(String name, String description, String modelType, Long dataSourceId,
                            String schemaName, String tableName, String definitionSql,
                            Long collectionId, List<FieldInput> fields) {
    }

    /**
     * 模型字段输入。
     */
    public record FieldInput(String name, String columnName, String fieldType, String aggregation) {
    }
}
