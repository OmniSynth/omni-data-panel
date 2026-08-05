package com.omni.panel.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 系统设置持久化实体（bi_setting），以键值对形式保存站点名称、嵌入开关、查询缓存等全局配置。
 */
@TableName("bi_setting")
public class SettingEntity {
    /**
     * 设置键，如 site.name、embed.enabled、cache.query.enabled。
     */
    @TableId
    private String settingKey;
    /**
     * 设置值。
     */
    private String settingValue;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
