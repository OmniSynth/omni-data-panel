package com.omni.panel.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
import com.omni.panel.common.ApiResponse;
import com.omni.panel.service.TotpService;
import com.omni.panel.service.UserService;

/**
 * 提供用户管理与轻量用户目录接口。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;
    private final TotpService totpService;

    /**
     * 注入用户与双因子认证服务。
     *
     * @param service      用户服务
     * @param totpService  双因子认证服务
     */
    public UserController(UserService service, TotpService totpService) {
        this.service = service;
        this.totpService = totpService;
    }

    /**
     * 查询启用用户的简要目录，供模型数据权限等选择器使用。
     *
     * @return 用户目录
     */
    @GetMapping("/directory")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<UserService.UserDirectoryItem>> directory() {
        return ApiResponse.ok(service.listDirectory());
    }

    /**
     * 查询全部用户及其角色权限。
     *
     * @return 用户列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserService.UserView> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(
                request.username(), request.password(), request.displayName(), request.email(),
                request.roleIds()));
    }

    /**
     * 更新用户资料和角色绑定。
     *
     * @param id      用户标识
     * @param request 用户更新参数
     * @return 更新后的用户
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserService.UserView> update(@PathVariable long id,
                                                    @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(service.update(id, request.displayName(), request.email(),
                request.enabled(), request.roleIds()));
    }

    /**
     * 重置普通用户密码；系统邮箱可用时发送重置链接。
     *
     * @param id      用户标识
     * @param request 新密码（邮件模式下可省略）
     * @return 空成功响应
     */
    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> resetPassword(@PathVariable long id,
                                           @RequestBody(required = false) PasswordRequest request) {
        service.resetPassword(id, request == null ? null : request.password());
        return ApiResponse.ok();
    }

    /**
     * 向未激活用户重发激活邮件。
     *
     * @param id 用户标识
     * @return 空成功响应
     */
    @PostMapping("/{id}/activation-email")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> resendActivation(@PathVariable long id) {
        service.resendActivation(id);
        return ApiResponse.ok();
    }

    /**
     * 管理员强制清除用户双因子认证。
     *
     * @param id 用户标识
     * @return 空成功响应
     */
    @PostMapping("/{id}/mfa/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> resetMfa(@PathVariable long id) {
        totpService.resetForUser(id);
        return ApiResponse.ok();
    }

    /**
     * 用户创建请求。
     *
     * @param username    唯一且创建后不可修改的用户名
     * @param password    初始密码；系统邮箱可用时可省略
     * @param displayName 展示名称
     * @param email       邮箱
     * @param roleIds     初始角色标识集合
     */
    public record CreateRequest(@NotBlank String username,
                                @Size(min = 10, message = "密码至少需要10位") String password,
                                @NotBlank String displayName,
                                @NotBlank @Email String email,
                                @NotEmpty List<Long> roleIds) {
    }

    /**
     * 用户资料更新请求。
     *
     * @param displayName 展示名称
     * @param email       邮箱；为空时保留原值
     * @param enabled     是否启用
     * @param roleIds     替换后的角色标识集合
     */
    public record UpdateRequest(@NotBlank String displayName,
                                String email,
                                @NotNull Boolean enabled,
                                @NotEmpty List<Long> roleIds) {
    }

    /**
     * 管理员密码重置请求。
     *
     * @param password 至少十位的新密码；邮件模式下可省略
     */
    public record PasswordRequest(
            @Size(min = 10, message = "密码至少需要10位") String password) {
    }
}
