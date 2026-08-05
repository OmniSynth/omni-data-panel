package com.omni.panel.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import com.omni.panel.entity.SysUser;

/**
 * 系统用户及其角色绑定的持久化访问接口，映射 {@code sys_user} 与 {@code sys_user_role} 等表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper} 提供用户 CRUD，自定义方法供认证与用户管理模块
 * 查询权限、角色关系及管理员判定。</p>
 */
public interface UserMapper extends BaseMapper<SysUser> {
    /**
     * 查询用户通过角色获得的全部权限编码。
     *
     * @param userId 用户标识
     * @return 权限编码列表
     */
    @Select("""
            SELECT DISTINCT p.code
            FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user_role ur ON ur.role_id = rp.role_id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.enabled = TRUE
            ORDER BY p.code
            """)
    List<String> findPermissions(long userId);

    /**
     * 查询用户当前关联的全部角色编码。
     *
     * @param userId 用户标识
     * @return 角色编码列表
     */
    @Select("""
            SELECT r.code
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.enabled = TRUE
            ORDER BY r.code
            """)
    List<String> findRoles(long userId);

    /**
     * 查询用户绑定的全部角色标识，包括当前已禁用角色。
     *
     * @param userId 用户标识
     * @return 角色标识列表
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId} ORDER BY role_id")
    List<Long> findRoleIds(long userId);

    /**
     * 查询全部用户。
     *
     * @return 按标识排列的用户列表
     */
    @Select("SELECT * FROM sys_user ORDER BY id")
    List<SysUser> findAll();

    /**
     * 删除用户的全部角色绑定。
     *
     * @param userId 用户标识
     * @return 受影响记录数
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteRoles(long userId);

    /**
     * 批量新增用户角色绑定。
     *
     * @param userId  用户标识
     * @param roleIds 角色标识集合
     * @return 新增数量
     */
    @Insert("""
            <script>
            INSERT INTO sys_user_role(user_id, role_id) VALUES
            <foreach collection="roleIds" item="roleId" separator=",">
                (#{userId}, #{roleId})
            </foreach>
            </script>
            """)
    int insertRoles(long userId, List<Long> roleIds);

    /**
     * 统计给定标识中的有效非管理员角色。
     *
     * @param roleIds 角色标识集合
     * @return 有效角色数量
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM sys_role
            WHERE enabled = TRUE AND code != 'ADMIN' AND id IN
            <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                #{roleId}
            </foreach>
            </script>
            """)
    int countAssignableRoles(List<Long> roleIds);

    /**
     * 判断用户是否关联启用的管理员角色。
     *
     * @param userId 用户标识
     * @return 管理员角色关联数量，大于 {@code 0} 表示是管理员
     */
    @Select("""
            SELECT COUNT(*)
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.code = 'ADMIN' AND r.enabled = TRUE
            """)
    int isAdmin(long userId);
}
