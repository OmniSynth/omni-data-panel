package com.omni.panel.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 模型（数据集）持久化实体（bi_dataset），支持表映射与 SQL 定义两种类型。
 */
@TableName("bi_dataset")
public class DatasetEntity {
    /**
     * 模型主键
     */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /**
     * 模型名称
     */
    private String name;
    /**
     * 模型说明
     */
    private String description;
    /**
     * 模型类型（表映射或 SQL）
     */
    private String modelType;
    /**
     * 所属数据源标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dataSourceId;
    /**
     * 数据库 schema 名称
     */
    private String schemaName;
    /**
     * 物理表名
     */
    private String tableName;
    /**
     * SQL 定义语句
     */
    private String definitionSql;
    /**
     * 所有者用户标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;
    /**
     * 所属集合标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long collectionId;
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    /**
     * 软删除时间；恢复时需写回 null，故更新策略为 ALWAYS。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getDefinitionSql() {
        return definitionSql;
    }

    public void setDefinitionSql(String definitionSql) {
        this.definitionSql = definitionSql;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(Long collectionId) {
        this.collectionId = collectionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
