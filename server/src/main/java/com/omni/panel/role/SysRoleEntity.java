package com.omni.panel.role;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 系统角色持久化实体（sys_role），定义角色编码、名称、启用状态及内置保护标记，供权限分配使用。
 */
@TableName("sys_role")
public class SysRoleEntity {
    /** 角色主键。 */
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 不可变角色编码，如 ADMIN、USER。 */
    private String code;
    /** 角色展示名称。 */
    private String name;
    /** 角色说明。 */
    private String description;
    /** 是否启用。 */
    private Boolean enabled;
    /** 是否为内置保护角色；内置角色不可删除或修改编码。 */
    private Boolean builtIn;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getBuiltIn() { return builtIn; }
    public void setBuiltIn(Boolean builtIn) { this.builtIn = builtIn; }
}
