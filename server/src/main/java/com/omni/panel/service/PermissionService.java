package com.omni.panel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.mapper.ResourceOwnerMapper;
import com.omni.panel.mapper.ResourcePermissionMapper;

/**
 * 统一校验和维护角色资源访问控制列表。
 *
 * <p>资源所有者和系统管理员天然拥有访问权；其他用户由资源直授 ACL 与集合祖先继承合并判定。
 * ACL 权限按 {@code WRITE → READ} 继承。个人集合禁止角色共享。</p>
 */
@Service
public class PermissionService {
    private static final Set<String> RESOURCE_TYPES = Set.of(
            "DATA_SOURCE", "COLLECTION", "CHART", "DASHBOARD", "DATASET", "METRIC");
    private static final Set<String> COLLECTION_INHERITABLE = Set.of(
            "COLLECTION", "CHART", "DASHBOARD", "DATASET", "METRIC");
    private static final int MAX_COLLECTION_DEPTH = 32;

    private final ResourcePermissionMapper mapper;
    private final ResourceOwnerMapper ownerMapper;

    public PermissionService(ResourcePermissionMapper mapper, ResourceOwnerMapper ownerMapper) {
        this.mapper = mapper;
        this.ownerMapper = ownerMapper;
    }

    /**
     * 要求当前用户拥有指定资源权限，否则拒绝访问。
     *
     * @param type       资源类型
     * @param resourceId 资源标识
     * @param ownerId    资源所有者标识
     * @param permission 要求的权限
     */
    public void require(String type, long resourceId, long ownerId, String permission) {
        AuthenticatedUser user = AuthenticatedUser.current();
        if (user.admin() || user.id() == ownerId) {
            return;
        }
        if (!satisfies(effectiveAclPermission(type, resourceId, user.id()), permission)) {
            throw new BusinessException(403, "无权访问该资源");
        }
    }

    /**
     * 判断当前用户是否可读取指定资源。
     *
     * @param type       资源类型
     * @param resourceId 资源标识
     * @param ownerId    资源所有者标识
     * @return 当前用户为管理员、所有者或具备有效读取权限时返回 {@code true}
     */
    public boolean canRead(String type, long resourceId, long ownerId) {
        AuthenticatedUser user = AuthenticatedUser.current();
        if (user.admin() || user.id() == ownerId) {
            return true;
        }
        return satisfies(effectiveAclPermission(type, resourceId, user.id()), "READ");
    }

    /**
     * 管理员或资源所有者可为角色授予或覆盖资源权限。
     *
     * @param type       资源类型
     * @param resourceId 资源标识
     * @param roleId     接收权限的角色标识
     * @param permission 要授予的权限
     */
    public void grant(String type, long resourceId, long roleId, String permission) {
        validatePermission(type, permission);
        long ownerId = owner(type, resourceId);
        requireGrantAuthority(type, resourceId, ownerId);
        if (type.equals("DATA_SOURCE") && !permission.equals("READ")) {
            throw new BusinessException("数据源仅允许授予 READ");
        }
        if (mapper.assignableRole(roleId) == 0) {
            throw new BusinessException("只能授权给启用的非管理员角色");
        }
        mapper.grant(type, resourceId, roleId, permission);
    }

    /**
     * 管理员或资源所有者可撤销角色资源权限。
     *
     * @param type       资源类型
     * @param resourceId 资源标识
     * @param roleId     被撤销权限的角色标识
     */
    public void revoke(String type, long resourceId, long roleId) {
        long ownerId = owner(type, resourceId);
        requireGrantAuthority(type, resourceId, ownerId);
        mapper.revoke(type, resourceId, roleId);
    }

    /**
     * 查询指定资源的全部 ACL，管理员或资源所有者可访问。
     *
     * @param type       资源类型
     * @param resourceId 资源标识
     * @return ACL 列表
     */
    public List<ResourcePermissionMapper.AclView> list(String type, long resourceId) {
        long ownerId = owner(type, resourceId);
        requireGrantAuthority(type, resourceId, ownerId);
        return mapper.list(type, resourceId);
    }

    /**
     * 查询当前用户在资源上的最高访问级别。
     *
     * @param type       资源类型
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
        String permission = effectiveAclPermission(type, resourceId, user.id());
        if (permission == null) {
            throw new BusinessException(403, "无权访问该资源");
        }
        return permission;
    }

    /**
     * 删除资源时同步清理全部 ACL。
     *
     * @param type       资源类型
     * @param resourceId 资源标识
     */
    public void deleteResource(String type, long resourceId) {
        mapper.deleteResource(type, resourceId);
    }

    /**
     * 合并用户对资源的直授 ACL 与集合祖先 ACL，取较高权限。
     *
     * @param type       资源类型
     * @param resourceId 资源标识
     * @param userId     用户标识
     * @return {@code WRITE}、{@code READ} 或 {@code null}
     */
    String effectiveAclPermission(String type, long resourceId, long userId) {
        String best = mapper.permission(type, resourceId, userId);
        if ("WRITE".equals(best) || !COLLECTION_INHERITABLE.contains(type)) {
            return best;
        }
        for (long collectionId : collectionAncestors(type, resourceId)) {
            String inherited = mapper.permission("COLLECTION", collectionId, userId);
            best = maxPermission(best, inherited);
            if ("WRITE".equals(best)) {
                return best;
            }
        }
        return best;
    }

    private List<Long> collectionAncestors(String type, long resourceId) {
        List<Long> chain = new ArrayList<>();
        Long cursor;
        if ("COLLECTION".equals(type)) {
            cursor = ownerMapper.collectionParentId(resourceId);
        } else {
            cursor = switch (type) {
                case "CHART" -> ownerMapper.chartCollectionId(resourceId);
                case "DASHBOARD" -> ownerMapper.dashboardCollectionId(resourceId);
                case "DATASET" -> ownerMapper.datasetCollectionId(resourceId);
                case "METRIC" -> ownerMapper.metricCollectionId(resourceId);
                default -> null;
            };
        }
        int depth = 0;
        while (cursor != null && depth < MAX_COLLECTION_DEPTH) {
            chain.add(cursor);
            Long parent = ownerMapper.collectionParentId(cursor);
            if (parent == null || parent.equals(cursor)) {
                break;
            }
            cursor = parent;
            depth++;
        }
        return chain;
    }

    private void requireGrantAuthority(String type, long resourceId, long ownerId) {
        AuthenticatedUser user = AuthenticatedUser.current();
        if (!user.admin() && user.id() != ownerId) {
            throw new BusinessException(403, "仅管理员或资源所有者可维护资源授权");
        }
        if ("COLLECTION".equals(type)) {
            Long personal = ownerMapper.collectionPersonalOwner(resourceId);
            if (personal != null) {
                throw new BusinessException("个人集合不可进行角色共享");
            }
        }
    }

    private void validatePermission(String type, String permission) {
        if (!RESOURCE_TYPES.contains(type) || !Set.of("READ", "WRITE").contains(permission)) {
            throw new BusinessException("资源类型或权限不合法");
        }
    }

    private long owner(String type, long resourceId) {
        Long owner = switch (type) {
            case "DATA_SOURCE" -> ownerMapper.dataSourceOwner(resourceId);
            case "DASHBOARD" -> ownerMapper.dashboardOwner(resourceId);
            case "CHART" -> ownerMapper.chartOwner(resourceId);
            case "DATASET" -> ownerMapper.datasetOwner(resourceId);
            case "METRIC" -> ownerMapper.metricOwner(resourceId);
            case "COLLECTION" -> ownerMapper.collectionOwner(resourceId);
            default -> throw new BusinessException("不支持的资源类型");
        };
        if (owner == null) {
            throw new BusinessException(404, "资源不存在");
        }
        return owner;
    }

    private static boolean satisfies(String actual, String required) {
        if (actual == null) {
            return false;
        }
        if ("READ".equals(required)) {
            return "READ".equals(actual) || "WRITE".equals(actual);
        }
        return "WRITE".equals(actual);
    }

    private static String maxPermission(String left, String right) {
        int l = rank(left);
        int r = rank(right);
        int best = Math.max(l, r);
        return best == 2 ? "WRITE" : best == 1 ? "READ" : null;
    }

    private static int rank(String permission) {
        if ("WRITE".equals(permission)) {
            return 2;
        }
        if ("READ".equals(permission)) {
            return 1;
        }
        return 0;
    }
}
