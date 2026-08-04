package com.omni.panel.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import com.omni.panel.entity.SysRoleEntity;

/**
 * 角色、功能权限及角色绑定关系的持久化访问接口，映射 {@code sys_role}、{@code sys_permission}、
 * {@code sys_role_permission} 等表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper} 提供角色 CRUD，自定义方法供角色管理模块维护
 * 功能权限绑定并统计关联用户与资源授权。</p>
 */
public interface RoleMapper extends BaseMapper<SysRoleEntity> {
    /**
     * 查询功能权限目录。
     *
     * @return 按编码排列的权限目录
     */
    @Select("SELECT id, code, name FROM sys_permission ORDER BY code")
    List<PermissionView> findPermissionCatalog();

    /**
     * 查询角色已配置的功能权限编码。
     *
     * @param roleId 角色标识
     * @return 权限编码列表
     */
    @Select("""
            SELECT p.code FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId}
            ORDER BY p.code
            """)
    List<String> findPermissionCodes(long roleId);

    /**
     * 删除角色的全部功能权限绑定。
     *
     * @param roleId 角色标识
     * @return 删除数量
     */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deletePermissions(long roleId);

    /**
     * 按权限编码为角色新增绑定。
     *
     * @param roleId          角色标识
     * @param permissionCodes 权限编码集合
     * @return 新增数量
     */
    @Insert("""
            <script>
            INSERT INTO sys_role_permission(role_id, permission_id)
            SELECT #{roleId}, id FROM sys_permission WHERE code IN
            <foreach collection="permissionCodes" item="code" open="(" separator="," close=")">
                #{code}
            </foreach>
            </script>
            """)
    int insertPermissions(long roleId, List<String> permissionCodes);

    /**
     * 统计角色的用户绑定数量。
     *
     * @param roleId 角色标识
     * @return 用户绑定数量
     */
    @Select("SELECT COUNT(*) FROM sys_user_role WHERE role_id = #{roleId}")
    int countUsers(long roleId);

    /**
     * 统计角色的资源授权数量。
     *
     * @param roleId 角色标识
     * @return 资源授权数量
     */
    @Select("SELECT COUNT(*) FROM bi_role_resource_permission WHERE role_id = #{roleId}")
    int countResources(long roleId);

    /**
     * 功能权限目录项。
     *
     * @param id   权限标识
     * @param code 权限编码
     * @param name 权限名称
     */
    record PermissionView(
            @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                    using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) long id,
            String code, String name) {
    }
}
