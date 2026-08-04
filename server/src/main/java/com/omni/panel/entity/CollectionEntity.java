package com.omni.panel.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 集合持久化实体（bi_collection），组织图表、仪表盘、模型与指标。
 */
@TableName("bi_collection")
public class CollectionEntity {
    /**
     * 集合主键
     */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /**
     * 集合名称
     */
    private String name;
    /**
     * 集合说明
     */
    private String description;
    /**
     * 父集合标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    /**
     * 个人空间所有者标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long personalOwnerId;
    /**
     * 所有者用户标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;
    /**
     * 是否已归档
     */
    private Boolean archived;
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

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getPersonalOwnerId() {
        return personalOwnerId;
    }

    public void setPersonalOwnerId(Long personalOwnerId) {
        this.personalOwnerId = personalOwnerId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
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
