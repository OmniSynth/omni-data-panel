package com.omni.panel.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.BusinessException;
import com.omni.panel.common.ClientRequestInfo;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.service.JwtService;
import com.omni.panel.service.LoginAuditService;

/**
 * 提供用户登录、当前身份查询与密码修改接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAuditService loginAuditService;

    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService,
                          LoginAuditService loginAuditService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAuditService = loginAuditService;
    }

    /**
     * 校验用户名和密码并签发访问令牌。
     *
     * @param request     登录凭据
     * @param httpRequest 当前 HTTP 请求，用于记录 IP 与浏览器
     * @return 访问令牌及令牌类型
     */
    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        ClientRequestInfo.Info client = ClientRequestInfo.from(httpRequest);
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.username()));
        if (user == null) {
            loginAuditService.record(request.username(), null, false, "用户不存在", client);
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            loginAuditService.record(request.username(), user.getId(), false, "账号已停用", client);
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAuditService.record(request.username(), user.getId(), false, "密码错误", client);
            throw new BusinessException(401, "用户名或密码错误");
        }
        loginAuditService.record(request.username(), user.getId(), true, "登录成功", client);
        return ApiResponse.ok(new LoginResult(jwtService.create(user.getId(), user.getUsername()), "Bearer"));
    }

    /**
     * 查询当前认证用户的基本信息与权限。
     *
     * @return 当前用户视图
     */
    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        AuthenticatedUser current = AuthenticatedUser.current();
        SysUser user = userMapper.selectById(current.id());
        return ApiResponse.ok(new UserView(user.getId(), user.getUsername(), user.getDisplayName(),
                current.roleCodes(), current.admin(), current.permissions()));
    }

    /**
     * 校验当前密码后更新登录密码。
     *
     * @param request 当前密码与新密码
     * @return 无数据的成功响应
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser current = AuthenticatedUser.current();
        SysUser user = userMapper.selectById(current.id());
        if (user == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("当前密码错误");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        return ApiResponse.ok();
    }

    /**
     * 登录请求。
     *
     * @param username 用户名
     * @param password 登录密码
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    /**
     * 密码修改请求。
     *
     * @param currentPassword 当前登录密码
     * @param newPassword     至少 10 位的新密码
     */
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, message = "新密码至少需要10位") String newPassword) {
    }

    /**
     * 登录成功结果。
     *
     * @param accessToken JWT 访问令牌
     * @param tokenType   令牌类型
     */
    public record LoginResult(String accessToken, String tokenType) {
    }

    /**
     * 当前用户视图。
     *
     * @param id          用户标识
     * @param username    用户名
     * @param displayName 展示名称
     * @param roles       当前角色编码列表
     * @param admin       是否具有管理员角色
     * @param permissions 权限编码列表
     */
    public record UserView(@JsonSerialize(using = ToStringSerializer.class) long id,
                           String username, String displayName, List<String> roles,
                           boolean admin, List<String> permissions) {
    }
}
