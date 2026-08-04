package com.omni.panel.role;

import com.omni.panel.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供仅管理员可调用的角色及功能权限目录接口。
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {
    private final RoleService service;

    /**
     * 创建角色控制器。
     *
     * @param service 角色服务
     */
    public RoleController(RoleService service) {
        this.service = service;
    }

    /**
     * 查询全部角色。
     *
     * @return 角色列表
     */
    @GetMapping("/roles")
    public ApiResponse<List<RoleService.RoleView>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * 查询功能权限目录。
     *
     * @return 权限目录
     */
    @GetMapping("/permissions")
    public ApiResponse<List<RoleMapper.PermissionView>> permissions() {
        return ApiResponse.ok(service.permissions());
    }

    /**
     * 创建自定义角色。
     *
     * @param request 角色创建参数
     * @return 新建角色
     */
    @PostMapping("/roles")
    public ApiResponse<RoleService.RoleView> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(
            request.code(), request.name(), request.description(), request.enabled()));
    }

    /**
     * 更新自定义角色，角色编码不可修改。
     *
     * @param id 角色标识
     * @param request 角色更新参数
     * @return 更新后的角色
     */
    @PutMapping("/roles/{id}")
    public ApiResponse<RoleService.RoleView> update(@PathVariable long id,
                                                    @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(service.update(id, request.name(), request.description(), request.enabled()));
    }

    /**
     * 删除未被使用的自定义角色。
     *
     * @param id 角色标识
     * @return 空成功响应
     */
    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 替换角色的功能权限集合。
     *
     * @param id 角色标识
     * @param request 功能权限编码集合
     * @return 更新后的角色
     */
    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<RoleService.RoleView> replacePermissions(
        @PathVariable long id, @Valid @RequestBody PermissionRequest request) {
        return ApiResponse.ok(service.replacePermissions(id, request.permissionCodes()));
    }

    /**
     * 角色创建请求。
     *
     * @param code 创建后不可修改的角色编码
     * @param name 角色名称
     * @param description 角色说明
     * @param enabled 是否启用
     */
    public record CreateRequest(@NotBlank String code, @NotBlank String name,
                                String description, @NotNull Boolean enabled) {}

    /**
     * 角色更新请求。
     *
     * @param name 角色名称
     * @param description 角色说明
     * @param enabled 是否启用
     */
    public record UpdateRequest(@NotBlank String name, String description, @NotNull Boolean enabled) {}

    /**
     * 角色功能权限替换请求。
     *
     * @param permissionCodes 权限编码集合
     */
    public record PermissionRequest(@NotNull List<String> permissionCodes) {}
}
