package com.omni.panel.recent;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 最近访问项持久化实体（bi_recent_item），记录用户对问题、仪表盘与模型的最近访问时间。
 */
@TableName("bi_recent_item")
public class RecentItemEntity {
    /** 记录主键。 */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 访问用户标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    /** 资源类型，如 QUESTION、DASHBOARD、MODEL。 */
    private String resourceType;
    /** 资源标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resourceId;
    /** 最近访问时间。 */
    private LocalDateTime visitedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public LocalDateTime getVisitedAt() { return visitedAt; }
    public void setVisitedAt(LocalDateTime visitedAt) { this.visitedAt = visitedAt; }
}
