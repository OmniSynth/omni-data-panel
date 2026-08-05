package com.omni.panel.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 公开分享链接持久化实体（bi_public_link），为仪表盘与图表生成免登录访问令牌并记录启用状态。
 */
@TableName("bi_public_link")
public class PublicLinkEntity {
    /**
     * 链接主键。
     */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /**
     * 被分享资源类型，如 DASHBOARD、QUESTION。
     */
    private String resourceType;
    /**
     * 被分享资源标识。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resourceId;
    /**
     * 公开访问令牌，用于 URL 鉴权。
     */
    private String token;
    /**
     * 是否启用；撤销时设为 false。
     */
    private Boolean enabled;
    /**
     * 创建者用户标识。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
