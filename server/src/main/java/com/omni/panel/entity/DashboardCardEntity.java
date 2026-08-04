package com.omni.panel.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 仪表盘卡片持久化实体（bi_dashboard_card），描述仪表盘与图表的关联及卡片布局。
 */
@TableName("bi_dashboard_card")
public class DashboardCardEntity {
    /**
     * 卡片主键
     */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /**
     * 所属仪表盘标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dashboardId;
    /**
     * 关联图表标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chartId;
    /**
     * 卡片标题
     */
    private String title;
    /**
     * 布局配置 JSON
     */
    private String layoutJson;
    /**
     * 参数绑定 JSON
     */
    private String bindingsJson;
    /**
     * 点击联动动作 JSON
     */
    private String clickActionJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDashboardId() {
        return dashboardId;
    }

    public void setDashboardId(Long dashboardId) {
        this.dashboardId = dashboardId;
    }

    public Long getChartId() {
        return chartId;
    }

    public void setChartId(Long chartId) {
        this.chartId = chartId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLayoutJson() {
        return layoutJson;
    }

    public void setLayoutJson(String layoutJson) {
        this.layoutJson = layoutJson;
    }

    public String getBindingsJson() {
        return bindingsJson;
    }

    public void setBindingsJson(String bindingsJson) {
        this.bindingsJson = bindingsJson;
    }

    public String getClickActionJson() {
        return clickActionJson;
    }

    public void setClickActionJson(String clickActionJson) {
        this.clickActionJson = clickActionJson;
    }
}
