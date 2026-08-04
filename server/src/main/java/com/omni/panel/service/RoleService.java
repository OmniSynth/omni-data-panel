package com.omni.panel.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.SysRoleEntity;
import com.omni.panel.mapper.RoleMapper;

/**
 * 管理自定义角色、功能权限配置和内置角色保护规则。
 */
@Service
public class RoleService {
    private final RoleMapper mapper;

    /**
     * 创建角色服务。
     *
     * @param mapper 角色持久化接口
     */
    public RoleService(RoleMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询全部角色及其功能权限。
     *
     * @return 角色视图列表
     */
    public List<RoleView> list() {
        requireAdmin();
        return mapper.selectList(Wrappers.<SysRoleEntity>lambdaQuery()
                        .orderByDesc(SysRoleEntity::getBuiltIn).orderByAsc(SysRoleEntity::getCode))
                .stream().map(this::view).toList();
    }

    /**
     * 查询功能权限目录。
     *
     * @return 权限目录
     */
    public List<RoleMapper.PermissionView> permissions() {
        requireAdmin();
        return mapper.findPermissionCatalog();
    }

    /**
     * 创建编码不可变的自定义角色。
     *
     * @param code        角色编码
     * @param name        角色名称
     * @param description 角色说明
     * @param enabled     是否启用
     * @return 新建角色
     */
    public RoleView create(String code, String name, String description, boolean enabled) {
        requireAdmin();
        String normalizedCode = normalizeCode(code);
        if (mapper.selectCount(Wrappers.<SysRoleEntity>lambdaQuery()
                .eq(SysRoleEntity::getCode, normalizedCode)) > 0) {
            throw new BusinessException("角色编码已存在");
        }
        SysRoleEntity role = new SysRoleEntity();
        role.setCode(normalizedCode);
        role.setName(name);
        role.setDescription(description);
        role.setEnabled(enabled);
        role.setBuiltIn(false);
        mapper.insert(role);
        return view(role);
    }

    /**
     * 更新自定义角色，角色编码保持不变。
     *
     * @param id          角色标识
     * @param name        角色名称
     * @param description 角色说明
     * @param enabled     是否启用
     * @return 更新后的角色
     */
    public RoleView update(long id, String name, String description, boolean enabled) {
        requireAdmin();
        SysRoleEntity role = requireRole(id);
        protectBuiltIn(role);
        role.setName(name);
        role.setDescription(description);
        role.setEnabled(enabled);
        mapper.updateById(role);
        return view(role);
    }

    /**
     * 删除未绑定用户和资源的自定义角色。
     *
     * @param id 角色标识
     */
    @Transactional
    public void delete(long id) {
        requireAdmin();
        SysRoleEntity role = requireRole(id);
        protectBuiltIn(role);
        if (mapper.countUsers(id) > 0 || mapper.countResources(id) > 0) {
            throw new BusinessException("角色仍绑定用户或资源，无法删除");
        }
        mapper.deletePermissions(id);
        mapper.deleteById(id);
    }

    /**
     * 原子替换自定义角色的功能权限集合。
     *
     * @param id              角色标识
     * @param permissionCodes 权限编码集合
     * @return 更新后的角色
     */
    @Transactional
    public RoleView replacePermissions(long id, List<String> permissionCodes) {
        requireAdmin();
        SysRoleEntity role = requireRole(id);
        protectBuiltIn(role);
        List<String> codes = permissionCodes == null ? List.of()
                : permissionCodes.stream().map(String::trim).distinct().toList();
        Set<String> catalog = mapper.findPermissionCatalog().stream()
                .map(RoleMapper.PermissionView::code).collect(java.util.stream.Collectors.toSet());
        if (!catalog.containsAll(codes)) {
            throw new BusinessException("包含不存在的功能权限");
        }
        mapper.deletePermissions(id);
        if (!codes.isEmpty() && mapper.insertPermissions(id, codes) != codes.size()) {
            throw new BusinessException("功能权限配置不完整");
        }
        return view(role);
    }

    /**
     * 将角色实体及其功能权限组装为对外视图。
     *
     * @param role 角色实体
     * @return 包含权限编码列表的角色视图
     */
    private RoleView view(SysRoleEntity role) {
        return new RoleView(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                Boolean.TRUE.equals(role.getEnabled()), Boolean.TRUE.equals(role.getBuiltIn()),
                mapper.findPermissionCodes(role.getId()));
    }

    /**
     * 按标识加载角色，不存在时抛出 404。
     *
     * @param id 角色标识
     * @return 存在的角色实体
     */
    private SysRoleEntity requireRole(long id) {
        SysRoleEntity role = mapper.selectById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        return role;
    }

    /**
     * 拒绝修改或删除内置角色及 ADMIN 角色。
     *
     * @param role 待操作的角色
     */
    private void protectBuiltIn(SysRoleEntity role) {
        if (Boolean.TRUE.equals(role.getBuiltIn()) || "ADMIN".equals(role.getCode())) {
            throw new BusinessException("ADMIN 内置角色不可编辑、删除或禁用");
        }
    }

    /**
     * 规范化角色编码为大写并校验格式。
     *
     * @param code 原始角色编码
     * @return 规范化后的角色编码
     */
    private String normalizeCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new BusinessException("角色编码须为2至64位大写字母、数字或下划线");
        }
        return normalized;
    }

    /**
     * 校验当前用户为管理员，否则拒绝访问。
     */
    private void requireAdmin() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可管理角色");
        }
    }

    /**
     * 角色对外视图。
     *
     * @param id          角色标识
     * @param code        不可变角色编码
     * @param name        角色名称
     * @param description 角色说明
     * @param enabled     是否启用
     * @param builtIn     是否为内置保护角色
     * @param permissions 功能权限编码列表
     */
    public record RoleView(@JsonSerialize(using = ToStringSerializer.class) long id,
                           String code, String name, String description, boolean enabled,
                           boolean builtIn, List<String> permissions) {
    }
}
