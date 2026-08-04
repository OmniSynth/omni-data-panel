package com.omni.panel.auth;

import com.omni.panel.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 当前认证用户的安全上下文身份。
 *
 * @param id 用户标识
 * @param username 用户名
 * @param roleCodes 用户当前拥有的启用角色编码
 * @param admin 是否具有管理员角色
 * @param permissions 用户拥有的权限编码
 */
public record AuthenticatedUser(long id, String username, List<String> roleCodes,
                                boolean admin, List<String> permissions) {
    /**
     * 构造仅显式区分管理员角色的兼容身份。
     *
     * @param id 用户标识
     * @param username 用户名
     * @param admin 是否为管理员
     * @param permissions 功能权限编码列表
     */
    public AuthenticatedUser(long id, String username, boolean admin, List<String> permissions) {
        this(id, username, admin ? List.of("ADMIN") : List.of(), admin, permissions);
    }

    /**
     * 获取当前请求的认证用户。
     *
     * @return 当前认证用户
     * @throws BusinessException 当前安全上下文中不存在已认证用户时抛出
     */
    public static AuthenticatedUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException(401, "未登录");
        }
        return user;
    }
}
