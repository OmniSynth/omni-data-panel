package com.omni.panel.permission;

import com.omni.panel.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 提供仅管理员可调用的角色资源授权接口。
 */
@RestController
@RequestMapping("/api/resources/{resourceType}/{resourceId}/permissions")
public class PermissionController {
    private final PermissionService service;

    public PermissionController(PermissionService service) {
        this.service = service;
    }

    /**
     * 为指定资源的角色授予或覆盖权限。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param request 目标角色和权限
     * @return 空成功响应
     */
    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> grant(@PathVariable String resourceType, @PathVariable long resourceId,
                                   @Valid @RequestBody GrantRequest request) {
        service.grant(resourceType.toUpperCase(), resourceId, request.roleId(), request.permission().toUpperCase());
        return ApiResponse.ok();
    }

    /**
     * 撤销指定角色在资源上的权限。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param roleId 被撤销权限的角色标识
     * @return 空成功响应
     */
    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> revoke(@PathVariable String resourceType, @PathVariable long resourceId,
                                    @PathVariable long roleId) {
        service.revoke(resourceType.toUpperCase(), resourceId, roleId);
        return ApiResponse.ok();
    }

    /**
     * 查询指定资源的全部 ACL。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @return ACL 列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<java.util.List<ResourcePermissionMapper.AclView>> list(
        @PathVariable String resourceType, @PathVariable long resourceId) {
        return ApiResponse.ok(service.list(resourceType.toUpperCase(), resourceId));
    }

    /**
     * 查询当前用户对指定资源的访问级别。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @return 当前访问级别
     */
    @GetMapping("/access")
    public ApiResponse<AccessView> access(@PathVariable String resourceType, @PathVariable long resourceId) {
        return ApiResponse.ok(new AccessView(service.accessLevel(resourceType.toUpperCase(), resourceId)));
    }

    /**
     * 资源授权请求。
     *
     * @param roleId 接收权限的角色标识
     * @param permission 要授予的权限
     */
    public record GrantRequest(@NotNull Long roleId, @NotBlank String permission) {}

    /**
     * 当前资源访问级别。
     *
     * @param accessLevel 访问级别
     */
    public record AccessView(String accessLevel) {}
}
