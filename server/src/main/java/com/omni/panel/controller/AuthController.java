package com.omni.panel.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.BusinessException;
import com.omni.panel.common.ClientRequestInfo;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.service.JwtService;
import com.omni.panel.service.LoginAuditService;
import com.omni.panel.service.LoginChallengeService;
import com.omni.panel.service.TotpService;
import com.omni.panel.service.UserService;
import com.omni.panel.service.UserSessionRegistry;

/**
 * 提供用户登录、双因子校验、当前身份查询与密码修改接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAuditService loginAuditService;
    private final UserService userService;
    private final TotpService totpService;
    private final LoginChallengeService loginChallengeService;
    private final UserSessionRegistry sessionRegistry;

    /**
     * 构造认证控制器并注入依赖。
     *
     * @param userMapper             用户数据访问
     * @param passwordEncoder        密码哈希校验
     * @param jwtService             JWT 签发与校验
     * @param loginAuditService      登录审计
     * @param userService            用户业务服务
     * @param totpService            双因子认证
     * @param loginChallengeService  登录挑战与签名校验
     * @param sessionRegistry        会话注册表
     */
    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService,
                          LoginAuditService loginAuditService, UserService userService, TotpService totpService,
                          LoginChallengeService loginChallengeService, UserSessionRegistry sessionRegistry) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAuditService = loginAuditService;
        this.userService = userService;
        this.totpService = totpService;
        this.loginChallengeService = loginChallengeService;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * 签发一次性登录挑战，供前端计算 HMAC 签名。
     *
     * @return 挑战信息
     */
    @GetMapping("/login-challenge")
    public ApiResponse<LoginChallengeService.ChallengeView> loginChallenge() {
        return ApiResponse.ok(loginChallengeService.issue());
    }

    /**
     * 校验签名、用户名和密码；若已启用 TOTP 则返回 MFA 中间令牌，否则签发访问令牌。
     *
     * @param request     登录凭据与签名
     * @param httpRequest 当前 HTTP 请求，用于记录 IP 与浏览器
     * @return 访问令牌或 MFA 挑战
     */
    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        ClientRequestInfo.Info client = ClientRequestInfo.from(httpRequest);
        try {
            loginChallengeService.verifyAndConsume(
                    request.challengeId(), request.nonce(), request.timestamp(),
                    request.username(), request.password(), request.signature());
        } catch (BusinessException exception) {
            loginAuditService.record(request.username(), null, false, "登录签名失败", client);
            throw exception;
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.username()));
        if (user == null) {
            loginAuditService.record(request.username(), null, false, "用户不存在", client);
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            loginAuditService.record(request.username(), user.getId(), false, "账号已停用", client);
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getActivated() != null && !user.getActivated()) {
            loginAuditService.record(request.username(), user.getId(), false, "账号未激活", client);
            throw new BusinessException(401, "账号未激活，请查收邮件设置密码");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAuditService.record(request.username(), user.getId(), false, "密码错误", client);
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            loginAuditService.record(request.username(), user.getId(), false, "需要MFA", client);
            return ApiResponse.ok(LoginResult.needMfa(jwtService.createMfaPending(user.getId(), user.getUsername())));
        }
        loginAuditService.record(request.username(), user.getId(), true, "登录成功", client);
        return ApiResponse.ok(LoginResult.bearer(issueAccessToken(user)));
    }

    /**
     * 使用 MFA 中间令牌与验证码完成登录。
     *
     * @param request     MFA 校验参数
     * @param httpRequest 当前请求
     * @return 正式访问令牌
     */
    @PostMapping("/mfa/verify")
    public ApiResponse<LoginResult> verifyMfa(@Valid @RequestBody MfaVerifyRequest request,
                                              HttpServletRequest httpRequest) {
        ClientRequestInfo.Info client = ClientRequestInfo.from(httpRequest);
        long userId = jwtService.requireMfaPendingUserId(request.mfaToken());
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            loginAuditService.record(user == null ? "" : user.getUsername(), userId, false, "MFA验证失败", client);
            throw new BusinessException(401, "MFA 验证失败");
        }
        if (!totpService.verifyLoginCode(userId, request.code())) {
            loginAuditService.record(user.getUsername(), userId, false, "MFA验证失败", client);
            throw new BusinessException(401, "验证码错误");
        }
        loginAuditService.record(user.getUsername(), userId, true, "登录成功(MFA)", client);
        return ApiResponse.ok(LoginResult.bearer(issueAccessToken(user)));
    }

    /**
     * 签发访问令牌并注册会话。
     *
     * @param user 已认证用户
     * @return JWT 访问令牌字符串
     */
    private String issueAccessToken(SysUser user) {
        JwtService.AccessToken access = jwtService.createAccess(user.getId(), user.getUsername());
        sessionRegistry.register(user.getId(), access.jti(), access.expiresAt());
        return access.token();
    }

    /**
     * 查询当前用户双因子状态。
     *
     * @return 是否已启用
     */
    @GetMapping("/mfa")
    public ApiResponse<MfaStatus> mfaStatus() {
        long userId = AuthenticatedUser.current().id();
        return ApiResponse.ok(new MfaStatus(totpService.isEnabled(userId)));
    }

    /**
     * 开始绑定双因子，返回密钥与 otpauth URI。
     *
     * @return 绑定信息
     */
    @PostMapping("/mfa/setup")
    public ApiResponse<TotpService.SetupInfo> beginMfaSetup() {
        return ApiResponse.ok(totpService.beginSetup(AuthenticatedUser.current().id()));
    }

    /**
     * 确认绑定并返回一次性备用码。
     *
     * @param request 动态验证码
     * @return 备用码列表
     */
    @PostMapping("/mfa/confirm")
    public ApiResponse<MfaConfirmResult> confirmMfa(@Valid @RequestBody MfaCodeRequest request) {
        List<String> backupCodes = totpService.confirmSetup(AuthenticatedUser.current().id(), request.code());
        return ApiResponse.ok(new MfaConfirmResult(backupCodes));
    }

    /**
     * 关闭双因子认证。
     *
     * @param request 当前密码与验证码
     * @return 空成功响应
     */
    @PostMapping("/mfa/disable")
    public ApiResponse<Void> disableMfa(@Valid @RequestBody MfaDisableRequest request) {
        AuthenticatedUser current = AuthenticatedUser.current();
        SysUser user = userMapper.selectById(current.id());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("当前密码错误");
        }
        totpService.disable(current.id(), request.code());
        return ApiResponse.ok();
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
                user.getEmail(), current.roleCodes(), current.admin(), current.permissions()));
    }

    /**
     * 校验设密令牌并返回账号预览信息。
     *
     * @param token 邮件中的原始令牌
     * @return 设密预览
     */
    @GetMapping("/setup-password")
    public ApiResponse<UserService.SetupPreview> previewSetup(@RequestParam String token) {
        return ApiResponse.ok(userService.previewSetup(token));
    }

    /**
     * 通过邮件令牌设置密码并完成激活（若为激活令牌）。
     *
     * @param request 令牌与新密码
     * @return 无数据的成功响应
     */
    @PostMapping("/setup-password")
    public ApiResponse<Void> completeSetup(@Valid @RequestBody SetupPasswordRequest request) {
        userService.completeSetup(request.token(), request.password());
        return ApiResponse.ok();
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
     * 登录请求（含防重放签名）。
     *
     * @param username    用户名
     * @param password    登录密码
     * @param challengeId 登录挑战标识
     * @param nonce       挑战随机数
     * @param timestamp   签名时间戳（秒）
     * @param signature   HMAC-SHA256 十六进制签名
     */
    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String challengeId,
            @NotBlank String nonce,
            @NotNull Long timestamp,
            @NotBlank String signature) {
    }

    /**
     * MFA 登录校验请求。
     *
     * @param mfaToken 密码通过后签发的中间令牌
     * @param code     TOTP 或备用码
     */
    public record MfaVerifyRequest(@NotBlank String mfaToken, @NotBlank String code) {
    }

    /**
     * 仅含验证码的请求。
     *
     * @param code 动态验证码
     */
    public record MfaCodeRequest(@NotBlank String code) {
    }

    /**
     * 关闭 MFA 请求。
     *
     * @param password 当前登录密码
     * @param code     TOTP 或备用码
     */
    public record MfaDisableRequest(@NotBlank String password, @NotBlank String code) {
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
     * 邮件令牌设密请求。
     *
     * @param token    邮件中的原始令牌
     * @param password 至少 10 位的新密码
     */
    public record SetupPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, message = "密码至少需要10位") String password) {
    }

    /**
     * 登录结果：正式令牌或 MFA 挑战。
     *
     * @param accessToken JWT 访问令牌；MFA 挑战时为 null
     * @param tokenType   令牌类型
     * @param mfaRequired 是否需要第二步
     * @param mfaToken    MFA 中间令牌
     */
    public record LoginResult(String accessToken, String tokenType, Boolean mfaRequired, String mfaToken) {
        /**
         * 签发正式访问令牌的结果。
         *
         * @param accessToken JWT 访问令牌
         * @return 登录成功结果
         */
        public static LoginResult bearer(String accessToken) {
            return new LoginResult(accessToken, "Bearer", false, null);
        }

        /**
         * 需要 MFA 校验的结果。
         *
         * @param mfaToken MFA 中间令牌
         * @return 待 MFA 校验的登录结果
         */
        public static LoginResult needMfa(String mfaToken) {
            return new LoginResult(null, null, true, mfaToken);
        }
    }

    /**
     * MFA 状态。
     *
     * @param enabled 是否已启用
     */
    public record MfaStatus(boolean enabled) {
    }

    /**
     * MFA 绑定确认结果。
     *
     * @param backupCodes 一次性备用码（仅此响应可见）
     */
    public record MfaConfirmResult(List<String> backupCodes) {
    }

    /**
     * 当前用户视图。
     *
     * @param id          用户标识
     * @param username    用户名
     * @param displayName 展示名称
     * @param email       邮箱
     * @param roles       当前角色编码列表
     * @param admin       是否具有管理员角色
     * @param permissions 权限编码列表
     */
    public record UserView(@JsonSerialize(using = ToStringSerializer.class) long id,
                           String username, String displayName, String email, List<String> roles,
                           boolean admin, List<String> permissions) {
    }
}
