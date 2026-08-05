package com.omni.panel.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 仪表盘邮件订阅持久化实体（bi_subscription），保存发送周期、收件人、所有者及启用状态。
 */
@TableName("bi_subscription")
public class SubscriptionEntity {
    /**
     * 订阅主键
     */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /**
     * 订阅名称
     */
    private String name;
    /**
     * 关联仪表盘标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dashboardId;
    /**
     * 发送周期 Cron 表达式
     */
    private String cronExpression;
    /**
     * 收件用户标识列表，逗号分隔；发送时解析为用户邮箱
     */
    private String recipients;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 所有者用户标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

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

    public Long getDashboardId() {
        return dashboardId;
    }

    public void setDashboardId(Long dashboardId) {
        this.dashboardId = dashboardId;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients = recipients;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
}
