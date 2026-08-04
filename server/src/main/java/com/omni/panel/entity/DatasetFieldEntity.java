package com.omni.panel.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 数据集字段持久化实体（bi_dataset_field），描述源字段的业务名称、维度或指标类型及聚合方式。
 */
@TableName("bi_dataset_field")
public class DatasetFieldEntity {
    /**
     * 字段主键
     */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /**
     * 所属数据集标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetId;
    /**
     * 字段业务名称
     */
    private String name;
    /**
     * 源列名
     */
    private String columnName;
    /**
     * 字段类型（维度或指标）
     */
    private String fieldType;
    /**
     * 聚合方式
     */
    private String aggregation;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getAggregation() {
        return aggregation;
    }

    public void setAggregation(String aggregation) {
        this.aggregation = aggregation;
    }
}
