package com.omni.panel.permission;

import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.List;

/**
 * 统一校验和维护角色资源访问控制列表。
 *
 * <p>资源所有者和系统管理员天然拥有访问权；其他用户由资源 ACL 判定。ACL 权限按
 * {@code WRITE → READ} 继承，管理员全权，资源所有者保留自身编辑权。</p>
 */
@Service
public class PermissionService {
    private static final Set<String> RESOURCE_TYPES = Set.of("DATA_SOURCE", "DASHBOARD");
    private final ResourcePermissionMapper mapper;
    private final ResourceOwnerMapper ownerMapper;

    public PermissionService(ResourcePermissionMapper mapper, ResourceOwnerMapper ownerMapper) {
        this.mapper = mapper;
        this.ownerMapper = ownerMapper;
    }

    /**
     * 要求当前用户拥有指定资源权限，否则拒绝访问。
     *
     * @param type 资源类型
     * @param resourceId 资源标识
     * @param ownerId 资源所有者标识
     * @param permission 要求的权限
     */
    public void require(String type, long resourceId, long ownerId, String permission) {
        AuthenticatedUser user = AuthenticatedUser.current();
        boolean roleShareable = RESOURCE_TYPES.contains(type);
        if (!user.admin() && user.id() != ownerId
            && !roleShareable) {
            throw new BusinessException(403, "无权访问该资源");
        }
        if (!user.admin() && user.id() != ownerId
            && mapper.hasPermission(type, resourceId, user.id(), permission) == 0) {
            throw new BusinessException(403, "无权访问该资源");
        }
    }

    /**
     * 判断当前用户是否可读取指定资源。
     *
     * @param type 资源类型
     * @param resourceId 资源标识
     * @param ownerId 资源所有者标识
     * @return 当前用户为管理员、所有者或启用角色授予读取能力时返回 {@code true}
     */
    public boolean canRead(String type, long resourceId, long ownerId) {
        AuthenticatedUser user = AuthenticatedUser.current();
        return user.admin() || user.id() == ownerId
            || (RESOURCE_TYPES.contains(type)
                && mapper.hasPermission(type, resourceId, user.id(), "READ") > 0);
    }

    /**
     * 仅管理员可为角色授予或覆盖资源权限。
     *
     * @param type 资源类型
     * @param resourceId 资源标识
     * @param roleId 接收权限的角色标识
     * @param permission 要授予的权限
     */
    public void grant(String type, long resourceId, long roleId, String permission) {
        requireAdministrator();
        validatePermission(type, permission);
        owner(type, resourceId);
        if (type.equals("DATA_SOURCE") && !permission.equals("READ")) {
            throw new BusinessException("数据源仅允许授予 READ");
        }
        if (mapper.assignableRole(roleId) == 0) {
            throw new BusinessException("只能授权给启用的非管理员角色");
        }
        mapper.grant(type, resourceId, roleId, permission);
    }

    /**
     * 仅管理员可撤销角色资源权限。
     *
     * @param type 资源类型
     * @param resourceId 资源标识
     * @param roleId 被撤销权限的角色标识
     */
    public void revoke(String type, long resourceId, long roleId) {
        requireAdministrator();
        owner(type, resourceId);
        mapper.revoke(type, resourceId, roleId);
    }

    /**
     * 查询指定资源的全部 ACL，仅管理员可访问。
     *
     * @param type 资源类型
     * @param resourceId 资源标识
     * @return ACL 列表
     */
    public List<ResourcePermissionMapper.AclView> list(String type, long resourceId) {
        requireAdministrator();
        owner(type, resourceId);
        return mapper.list(type, resourceId);
    }

    /**
     * 查询当前用户在资源上的最高访问级别。
     *
     * @param type 资源类型
     * @param resourceId 资源标识
     * @return {@code OWNER}、{@code ADMIN}、{@code WRITE} 或 {@code READ}
     */
    public String accessLevel(String type, long resourceId) {
        long ownerId = owner(type, resourceId);
        AuthenticatedUser user = AuthenticatedUser.current();
        if (user.admin()) {
            return "ADMIN";
        }
        if (user.id() == ownerId) {
            return "OWNER";
        }
        String permission = mapper.permission(type, resourceId, user.id());
        if (permission == null) {
            throw new BusinessException(403, "无权访问该资源");
        }
        return permission;
    }

    /**
     * 删除资源时同步清理全部 ACL。
     *
     * @param type 资源类型
     * @param resourceId 资源标识
     */
    public void deleteResource(String type, long resourceId) {
        mapper.deleteResource(type, resourceId);
    }

    private void validatePermission(String type, String permission) {
        if (!RESOURCE_TYPES.contains(type) || !Set.of("READ", "WRITE").contains(permission)) {
            throw new BusinessException("资源类型或权限不合法");
        }
    }

    private void requireAdministrator() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可维护资源授权");
        }
    }

    private long owner(String type, long resourceId) {
        Long owner = switch (type) {
            case "DATA_SOURCE" -> ownerMapper.dataSourceOwner(resourceId);
            case "DASHBOARD" -> ownerMapper.dashboardOwner(resourceId);
            default -> throw new BusinessException("不支持的资源类型");
        };
        if (owner == null) {
            throw new BusinessException(404, "资源不存在");
        }
        return owner;
    }
}
