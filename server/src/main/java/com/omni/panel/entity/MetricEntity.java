package com.omni.panel.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 指标持久化实体（bi_metric），绑定模型字段或表达式聚合。
 */
@TableName("bi_metric")
public class MetricEntity {
    /**
     * 指标主键
     */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /**
     * 指标名称
     */
    private String name;
    /**
     * 指标说明
     */
    private String description;
    /**
     * 所属模型标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;
    /**
     * 表达式配置 JSON
     */
    private String expressionJson;
    /**
     * 聚合方式
     */
    private String aggregation;
    /**
     * 所属集合标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long collectionId;
    /**
     * 所有者用户标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;
    /**
     * 软删除时间；恢复时需写回 null，故更新策略为 ALWAYS。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime deletedAt;
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

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

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getExpressionJson() {
        return expressionJson;
    }

    public void setExpressionJson(String expressionJson) {
        this.expressionJson = expressionJson;
    }

    public String getAggregation() {
        return aggregation;
    }

    public void setAggregation(String aggregation) {
        this.aggregation = aggregation;
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(Long collectionId) {
        this.collectionId = collectionId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
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
}
