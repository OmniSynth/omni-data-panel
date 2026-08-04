package com.omni.panel.auth;

import com.omni.panel.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供仅管理员可调用的多角色用户管理接口。
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /**
     * 查询全部用户及其角色权限。
     *
     * @return 用户列表
     */
    @GetMapping
    public ApiResponse<List<UserService.UserView>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * 创建用户并绑定多个角色。
     *
     * @param request 用户创建参数
     * @return 新建用户
     */
    @PostMapping
    public ApiResponse<UserService.UserView> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(
            request.username(), request.password(), request.displayName(), request.roleIds()));
    }

    /**
     * 更新用户资料和角色绑定。
     *
     * @param id 用户标识
     * @param request 用户更新参数
     * @return 更新后的用户
     */
    @PutMapping("/{id}")
    public ApiResponse<UserService.UserView> update(@PathVariable long id,
                                                    @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(service.update(id, request.displayName(), request.enabled(), request.roleIds()));
    }

    /**
     * 重置普通用户密码。
     *
     * @param id 用户标识
     * @param request 新密码
     * @return 空成功响应
     */
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable long id,
                                           @Valid @RequestBody PasswordRequest request) {
        service.resetPassword(id, request.password());
        return ApiResponse.ok();
    }

    /**
     * 用户创建请求。
     *
     * @param username 唯一且创建后不可修改的用户名
     * @param password 至少十位的初始密码
     * @param displayName 展示名称
     * @param roleIds 初始角色标识集合
     */
    public record CreateRequest(@NotBlank String username,
                                @NotBlank @Size(min = 10, message = "密码至少需要10位") String password,
                                @NotBlank String displayName,
                                @NotEmpty List<Long> roleIds) {}

    /**
     * 用户资料更新请求。
     *
     * @param displayName 展示名称
     * @param enabled 是否启用
     * @param roleIds 替换后的角色标识集合
     */
    public record UpdateRequest(@NotBlank String displayName, @NotNull Boolean enabled,
                                @NotEmpty List<Long> roleIds) {}

    /**
     * 管理员密码重置请求。
     *
     * @param password 至少十位的新密码
     */
    public record PasswordRequest(
        @NotBlank @Size(min = 10, message = "密码至少需要10位") String password) {}
}
