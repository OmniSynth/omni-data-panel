package com.omni.panel.mapper;

import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/**
 * 角色资源访问控制列表持久化接口，映射 {@code bi_role_resource_permission} 表。
 *
 * <p>供 {@link com.omni.panel.service.PermissionService} 授予、撤销与计算用户对资源的最高权限。
 * 权限判定包含继承关系：{@code ADMIN} 满足任意权限要求，{@code WRITE} 同时满足
 * {@code READ} 要求。</p>
 */
public interface ResourcePermissionMapper {
    /**
     * 统计用户的启用角色是否满足指定资源权限。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param userId       用户标识
     * @param permission   要求的权限
     * @return 匹配的 ACL 记录数
     */
    @Select("""
            SELECT COUNT(*) FROM bi_role_resource_permission rp
            JOIN sys_role r ON r.id = rp.role_id AND r.enabled = TRUE
            JOIN sys_user_role ur ON ur.role_id = r.id AND ur.user_id = #{userId}
            WHERE rp.resource_type = #{resourceType} AND rp.resource_id = #{resourceId}
              AND (
                rp.permission = #{permission}
                OR (#{permission} = 'READ' AND rp.permission = 'WRITE')
              )
            """)
    int hasPermission(String resourceType, long resourceId, long userId, String permission);

    /**
     * 新增角色 ACL；同一角色和资源已有记录时覆盖权限。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param roleId       角色标识
     * @param permission   要授予的权限
     * @return 受影响的记录数
     */
    @Insert("""
            INSERT INTO bi_role_resource_permission(role_id, resource_type, resource_id, permission)
            VALUES(#{roleId}, #{resourceType}, #{resourceId}, #{permission})
            ON DUPLICATE KEY UPDATE permission = VALUES(permission)
            """)
    int grant(String resourceType, long resourceId, long roleId, String permission);

    /**
     * 删除角色在指定资源上的 ACL。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param roleId       角色标识
     * @return 受影响的记录数
     */
    @Delete("""
            DELETE FROM bi_role_resource_permission
            WHERE resource_type = #{resourceType} AND resource_id = #{resourceId} AND role_id = #{roleId}
            """)
    int revoke(String resourceType, long resourceId, long roleId);

    /**
     * 查询资源的全部角色 ACL 记录及角色资料。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @return ACL 视图列表
     */
    @Select("""
            SELECT rp.role_id, r.code, r.name, rp.permission
            FROM bi_role_resource_permission rp
            JOIN sys_role r ON r.id = rp.role_id
            WHERE rp.resource_type = #{resourceType} AND rp.resource_id = #{resourceId}
            ORDER BY r.code
            """)
    List<AclView> list(String resourceType, long resourceId);

    /**
     * 查询用户通过全部启用角色获得的最高资源权限。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param userId       用户标识
     * @return 权限编码，不存在时返回 {@code null}
     */
    @Select("""
            SELECT CASE MAX(CASE rp.permission WHEN 'WRITE' THEN 2 WHEN 'READ' THEN 1 ELSE 0 END)
                WHEN 2 THEN 'WRITE' WHEN 1 THEN 'READ' ELSE NULL END
            FROM bi_role_resource_permission rp
            JOIN sys_role r ON r.id = rp.role_id AND r.enabled = TRUE
            JOIN sys_user_role ur ON ur.role_id = r.id AND ur.user_id = #{userId}
            WHERE rp.resource_type = #{resourceType} AND rp.resource_id = #{resourceId}
            """)
    String permission(String resourceType, long resourceId, long userId);

    /**
     * 删除资源的全部 ACL 记录。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @return 受影响记录数
     */
    @Delete("""
            DELETE FROM bi_role_resource_permission
            WHERE resource_type = #{resourceType} AND resource_id = #{resourceId}
            """)
    int deleteResource(String resourceType, long resourceId);

    /**
     * 判断角色是否可用于资源授权。
     *
     * @param roleId 角色标识
     * @return 启用且非管理员角色数量
     */
    @Select("SELECT COUNT(*) FROM sys_role WHERE id = #{roleId} AND enabled = TRUE AND code != 'ADMIN'")
    int assignableRole(long roleId);

    /**
     * 角色资源 ACL 列表项。
     *
     * @param roleId     角色标识
     * @param code       角色编码
     * @param name       角色名称
     * @param permission 权限编码
     */
    record AclView(@JsonSerialize(using = ToStringSerializer.class) long roleId,
                   String code, String name, String permission) {
    }
}
