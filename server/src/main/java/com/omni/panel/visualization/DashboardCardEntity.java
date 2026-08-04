package com.omni.panel.visualization;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 仪表盘卡片持久化实体，描述仪表盘与图表的关联及卡片布局。
 */
@TableName("bi_dashboard_card")
public class DashboardCardEntity {
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dashboardId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chartId;
    private String title;
    private String layoutJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDashboardId() { return dashboardId; }
    public void setDashboardId(Long dashboardId) { this.dashboardId = dashboardId; }
    public Long getChartId() { return chartId; }
    public void setChartId(Long chartId) { this.chartId = chartId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLayoutJson() { return layoutJson; }
    public void setLayoutJson(String layoutJson) { this.layoutJson = layoutJson; }
}
