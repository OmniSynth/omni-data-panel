package com.omni.panel.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.omni.panel.common.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理普通用户及其多角色绑定，保护管理员账户和不可变用户名边界。
 */
@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 查询全部用户及其实时角色、权限。
     *
     * @return 用户视图列表
     */
    public List<UserView> list() {
        requireAdmin();
        return userMapper.findAll().stream().map(this::view).toList();
    }

    /**
     * 创建启用用户并原子绑定请求指定的多个角色。
     *
     * @param username 登录用户名
     * @param password 初始密码
     * @param displayName 展示名称
     * @param roleIds 角色标识集合
     * @return 新建用户视图
     */
    @Transactional
    public UserView create(String username, String password, String displayName, List<Long> roleIds) {
        requireAdmin();
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0) {
            throw new BusinessException("用户名已存在");
        }
        List<Long> roles = validateRoles(roleIds);
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setEnabled(true);
        userMapper.insert(user);
        replaceRoles(user.getId(), roles);
        return view(user);
    }

    /**
     * 更新普通用户展示名称、启用状态和多角色绑定。
     *
     * @param id 用户标识
     * @param displayName 展示名称
     * @param enabled 是否启用
     * @param roleIds 角色标识集合
     * @return 更新后的用户视图
     */
    @Transactional
    public UserView update(long id, String displayName, boolean enabled, List<Long> roleIds) {
        requireAdmin();
        SysUser user = requireMutableUser(id);
        List<Long> roles = validateRoles(roleIds);
        user.setDisplayName(displayName);
        user.setEnabled(enabled);
        userMapper.updateById(user);
        replaceRoles(id, roles);
        return view(user);
    }

    /**
     * 重置普通用户登录密码。
     *
     * @param id 用户标识
     * @param password 新密码
     */
    @Transactional
    public void resetPassword(long id, String password) {
        requireAdmin();
        SysUser user = requireMutableUser(id);
        user.setPasswordHash(passwordEncoder.encode(password));
        userMapper.updateById(user);
    }

    private SysUser requireMutableUser(long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (userMapper.isAdmin(id) > 0) {
            throw new BusinessException("ADMIN 账号不可禁用、重置或改动");
        }
        return user;
    }

    private List<Long> validateRoles(List<Long> roleIds) {
        List<Long> roles = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        if (roles.isEmpty() || userMapper.countAssignableRoles(roles) != roles.size()) {
            throw new BusinessException("用户必须绑定至少一个有效的非管理员角色");
        }
        return roles;
    }

    private void replaceRoles(long userId, List<Long> roleIds) {
        userMapper.deleteRoles(userId);
        if (userMapper.insertRoles(userId, roleIds) != roleIds.size()) {
            throw new BusinessException("用户角色绑定不完整");
        }
    }

    private void requireAdmin() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可管理用户");
        }
    }

    private UserView view(SysUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(),
            user.getEnabled(), userMapper.findRoleIds(user.getId()), userMapper.findRoles(user.getId()),
            userMapper.findPermissions(user.getId()));
    }

    /**
     * 用户管理视图。
     *
     * @param id 用户标识
     * @param username 不可变登录用户名
     * @param displayName 展示名称
     * @param enabled 是否启用
     * @param roleIds 已绑定角色标识列表
     * @param roles 当前启用角色编码列表
     * @param permissions 当前启用角色的功能权限并集
     */
    public record UserView(@JsonSerialize(using = ToStringSerializer.class) long id,
                           String username, String displayName, boolean enabled,
                           @JsonSerialize(contentUsing = ToStringSerializer.class) List<Long> roleIds,
                           List<String> roles, List<String> permissions) {}
}
