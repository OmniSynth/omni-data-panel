package com.omni.panel.service;

import java.util.ArrayList;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.UserMapper;

/**
 * 根据数据库中的实时账户状态、角色和权限构造认证对象。
 */
@Service
public class UserAuthenticationService {
    private final UserMapper userMapper;

    public UserAuthenticationService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 加载启用用户并构造包含角色与业务权限的认证对象。
     *
     * @param userId 用户标识
     * @return 用户当前认证对象
     * @throws BusinessException 用户不存在或已禁用时抛出
     */
    public Authentication load(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(401, "用户不存在或已禁用");
        }
        var roles = userMapper.findRoles(userId);
        var permissions = userMapper.findPermissions(userId);
        boolean admin = roles.contains("ADMIN");
        var principal = new AuthenticatedUser(userId, user.getUsername(), roles, admin, permissions);
        var authorities = new ArrayList<>(permissions.stream().map(SimpleGrantedAuthority::new).toList());
        roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).forEach(authorities::add);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
