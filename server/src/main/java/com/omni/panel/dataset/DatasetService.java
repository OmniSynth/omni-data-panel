package com.omni.panel.dataset;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.collection.CollectionService;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.DataSourceService;
import com.omni.panel.metadata.MetadataMapper;
import com.omni.panel.permission.PermissionService;
import com.omni.panel.query.SqlPolicyGuard;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 管理模型（数据集）及字段定义，支持表模型与 SQL 模型。
 */
@Service
public class DatasetService {
    private static final Set<String> AGGREGATIONS = Set.of("SUM", "COUNT", "AVG", "MIN", "MAX");
    private static final Set<String> MODEL_TYPES = Set.of("TABLE", "SQL");
    private final DatasetMapper datasetMapper;
    private final DatasetFieldMapper fieldMapper;
    private final MetadataMapper metadataMapper;
    private final DataSourceService dataSourceService;
    private final PermissionService permissionService;
    private final SqlPolicyGuard sqlPolicyGuard;
    private final CollectionService collectionService;

    public DatasetService(DatasetMapper datasetMapper, DatasetFieldMapper fieldMapper, MetadataMapper metadataMapper,
                          DataSourceService dataSourceService, PermissionService permissionService,
                          SqlPolicyGuard sqlPolicyGuard, @Lazy CollectionService collectionService) {
        this.datasetMapper = datasetMapper;
        this.fieldMapper = fieldMapper;
        this.metadataMapper = metadataMapper;
        this.dataSourceService = dataSourceService;
        this.permissionService = permissionService;
        this.sqlPolicyGuard = sqlPolicyGuard;
        this.collectionService = collectionService;
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
     * @param id 模型标识
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
        return dataset;
    }

    /**
     * 更新模型并以新字段定义整体替换。
     *
     * @param id 模型标识
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
    }

    /**
     * 永久删除模型。
     *
     * @param id 模型标识
     */
    @Transactional
    public void purge(long id) {
        DatasetEntity dataset = requireTrashed(id);
        permissionService.deleteResource("DATASET", id);
        datasetMapper.deleteById(dataset.getId());
    }

    private long resolveCollectionId(Long collectionId, long userId) {
        if (collectionId != null) {
            collectionService.requireReadable(collectionId);
            return collectionId;
        }
        return collectionService.ensurePersonalCollection(userId).getId();
    }

    private String normalizeModelType(String modelType) {
        String normalized = modelType == null || modelType.isBlank() ? "TABLE" : modelType.toUpperCase();
        if (!MODEL_TYPES.contains(normalized)) {
            throw new BusinessException("模型类型仅支持 TABLE 或 SQL");
        }
        return normalized;
    }

    private void applyModelFields(DatasetEntity dataset, String modelType, SaveInput input) {
        if ("SQL".equals(modelType)) {
            if (input.definitionSql() == null || input.definitionSql().isBlank()) {
                throw new BusinessException("SQL 模型必须提供 definitionSql");
            }
            sqlPolicyGuard.validate(input.definitionSql());
            dataset.setDefinitionSql(input.definitionSql());
            dataset.setSchemaName(blankToNull(input.schemaName()));
            dataset.setTableName(blankToNull(input.tableName()));
            return;
        }
        if (input.schemaName() == null || input.schemaName().isBlank()
            || input.tableName() == null || input.tableName().isBlank()) {
            throw new BusinessException("表模型必须提供 schemaName 与 tableName");
        }
        dataset.setSchemaName(input.schemaName());
        dataset.setTableName(input.tableName());
        dataset.setDefinitionSql(null);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

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
                            Long collectionId, List<FieldInput> fields) {}

    /**
     * 模型字段输入。
     */
    public record FieldInput(String name, String columnName, String fieldType, String aggregation) {}
}
