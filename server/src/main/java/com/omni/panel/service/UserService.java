package com.omni.panel.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.SysUser;
import com.omni.panel.entity.UserCredentialTokenEntity;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.subscription.SubscriptionProperties;

/**
 * 管理普通用户及其多角色绑定，保护管理员账户和不可变用户名边界。
 */
@Service
public class UserService {
    private static final int MIN_PASSWORD_LENGTH = 10;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SystemMailService mailService;
    private final UserCredentialTokenService tokenService;
    private final SubscriptionProperties subscriptionProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 注入用户持久化、密码哈希、邮件与凭据令牌依赖。
     *
     * @param userMapper             用户持久化
     * @param passwordEncoder        密码哈希
     * @param mailService            系统邮件
     * @param tokenService           凭据令牌
     * @param subscriptionProperties 订阅配置（前端链接基址）
     */
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, SystemMailService mailService,
                       UserCredentialTokenService tokenService, SubscriptionProperties subscriptionProperties) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.tokenService = tokenService;
        this.subscriptionProperties = subscriptionProperties;
    }

    /**
     * 查询全部用户及其实时角色、权限。
     *
     * @return 用户视图列表
     */
    public List<UserView> list() {
        requireAdmin();
        return userMapper.findAll().stream().map(this::view).toList();
    }

    /**
     * 查询已启用且已激活用户的简要目录（不含角色与权限），供选择器使用。
     *
     * @return 用户目录项
     */
    public List<UserDirectoryItem> listDirectory() {
        return userMapper.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()) && isActivated(user))
                .map(user -> new UserDirectoryItem(
                        user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail()))
                .toList();
    }

    /**
     * 创建用户并绑定角色。系统邮箱可用时发激活链接且无需初始密码；否则要求管理员设置初始密码。
     *
     * @param username    登录用户名
     * @param password    初始密码；邮件邀请模式下可为空
     * @param displayName 展示名称
     * @param email       邮箱
     * @param roleIds     角色标识集合
     * @return 新建用户视图
     */
    @Transactional
    public UserView create(String username, String password, String displayName, String email,
                           List<Long> roleIds) {
        requireAdmin();
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0) {
            throw new BusinessException("用户名已存在");
        }
        String normalizedEmail = normalizeEmail(email);
        ensureEmailAvailable(normalizedEmail, null);
        List<Long> roles = validateRoles(roleIds);
        boolean invite = mailService.ready();
        if (!invite) {
            requirePassword(password);
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(invite ? randomSecret() : password));
        user.setDisplayName(displayName);
        user.setEmail(normalizedEmail);
        user.setEnabled(true);
        user.setActivated(!invite);
        user.setActivatedAt(invite ? null : LocalDateTime.now());
        userMapper.insert(user);
        replaceRoles(user.getId(), roles);
        if (invite) {
            sendCredentialMail(user, UserCredentialTokenEntity.PURPOSE_ACTIVATE);
        }
        return view(user);
    }

    /**
     * 更新普通用户展示名称、邮箱、启用状态和多角色绑定。
     *
     * @param id          用户标识
     * @param displayName 展示名称
     * @param email       邮箱
     * @param enabled     是否启用
     * @param roleIds     角色标识集合
     * @return 更新后的用户视图
     */
    @Transactional
    public UserView update(long id, String displayName, String email, boolean enabled, List<Long> roleIds) {
        requireAdmin();
        SysUser user = requireMutableUser(id);
        List<Long> roles = validateRoles(roleIds);
        user.setDisplayName(displayName);
        if (email != null && !email.isBlank()) {
            String normalizedEmail = normalizeEmail(email);
            ensureEmailAvailable(normalizedEmail, id);
            user.setEmail(normalizedEmail);
        }
        user.setEnabled(enabled);
        userMapper.updateById(user);
        replaceRoles(id, roles);
        return view(user);
    }

    /**
     * 重置普通用户登录密码；系统邮箱可用时改为发送重置链接。
     *
     * @param id       用户标识
     * @param password 新密码；邮件模式下忽略
     */
    @Transactional
    public void resetPassword(long id, String password) {
        requireAdmin();
        SysUser user = requireMutableUser(id);
        if (mailService.ready()) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                throw new BusinessException("用户未配置邮箱，无法发送重置链接");
            }
            sendCredentialMail(user, UserCredentialTokenEntity.PURPOSE_RESET);
            return;
        }
        requirePassword(password);
        user.setPasswordHash(passwordEncoder.encode(password));
        if (!isActivated(user)) {
            user.setActivated(true);
            user.setActivatedAt(LocalDateTime.now());
        }
        userMapper.updateById(user);
    }

    /**
     * 向未激活用户重新发送激活邮件。
     *
     * @param id 用户标识
     */
    @Transactional
    public void resendActivation(long id) {
        requireAdmin();
        if (!mailService.ready()) {
            throw new BusinessException(503, "邮件服务未配置");
        }
        SysUser user = requireMutableUser(id);
        if (isActivated(user)) {
            throw new BusinessException("用户已激活");
        }
        sendCredentialMail(user, UserCredentialTokenEntity.PURPOSE_ACTIVATE);
    }

    /**
     * 根据令牌查询设密页所需信息。
     *
     * @param rawToken 原始令牌
     * @return 设密预览
     */
    public SetupPreview previewSetup(String rawToken) {
        UserCredentialTokenEntity token = tokenService.requireValid(rawToken);
        SysUser user = userMapper.selectById(token.getUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException("链接无效或已过期");
        }
        return new SetupPreview(user.getUsername(), user.getDisplayName(), token.getPurpose());
    }

    /**
     * 通过令牌设置密码；激活令牌同时完成账号激活。
     *
     * @param rawToken 原始令牌
     * @param password 新密码
     */
    @Transactional
    public void completeSetup(String rawToken, String password) {
        requirePassword(password);
        UserCredentialTokenEntity peeked = tokenService.requireValid(rawToken);
        UserCredentialTokenEntity token = tokenService.consume(rawToken, peeked.getPurpose());
        SysUser user = userMapper.selectById(token.getUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException("链接无效或已过期");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        if (UserCredentialTokenEntity.PURPOSE_ACTIVATE.equals(token.getPurpose()) || !isActivated(user)) {
            user.setActivated(true);
            user.setActivatedAt(LocalDateTime.now());
        }
        userMapper.updateById(user);
    }

    /**
     * 签发凭据令牌并向用户发送激活或重置密码邮件。
     *
     * @param user    目标用户
     * @param purpose 令牌用途（激活或重置）
     */
    private void sendCredentialMail(SysUser user, String purpose) {
        String raw = tokenService.issue(user.getId(), purpose);
        String baseUrl = subscriptionProperties.getFrontendUrl() == null
                ? "" : subscriptionProperties.getFrontendUrl().replaceAll("/+$", "");
        String link = baseUrl + "/setup-password?token=" + raw;
        boolean activate = UserCredentialTokenEntity.PURPOSE_ACTIVATE.equals(purpose);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject(activate ? "激活账号并设置密码" : "重置登录密码");
        message.setText((activate
                ? "您好 " + user.getDisplayName() + "，\n\n管理员已为您创建账号「" + user.getUsername()
                + "」。请点击以下链接设置密码并激活账号（48 小时内有效）：\n"
                : "您好 " + user.getDisplayName() + "，\n\n请点击以下链接为账号「" + user.getUsername()
                + "」设置新密码（24 小时内有效）：\n")
                + link + "\n\n如非本人操作请忽略本邮件。");
        mailService.send(message);
    }

    /**
     * 校验密码非空且满足最小长度要求。
     *
     * @param password 待校验密码
     */
    private static void requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException("密码不能为空");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("密码至少需要10位");
        }
    }

    /**
     * 生成用于占位密码的随机 URL 安全字符串。
     *
     * @return Base64 URL 编码的随机密钥
     */
    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 判断用户是否已完成账号激活。
     *
     * @param user 用户实体
     * @return 已激活或未设置激活状态时返回 {@code true}
     */
    private static boolean isActivated(SysUser user) {
        return user.getActivated() == null || Boolean.TRUE.equals(user.getActivated());
    }

    /**
     * 规范化邮箱并校验非空。
     *
     * @param email 原始邮箱
     * @return 小写去空白后的邮箱
     */
    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("邮箱不能为空");
        }
        String normalized = email.trim().toLowerCase();
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException("邮箱格式不正确");
        }
        return normalized;
    }

    /**
     * 确保邮箱未被其他用户占用。
     *
     * @param email         规范化邮箱
     * @param excludeUserId 更新时排除的用户标识；创建时传 {@code null}
     */
    private void ensureEmailAvailable(String email, Long excludeUserId) {
        var query = Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEmail, email);
        if (excludeUserId != null) {
            query.ne(SysUser::getId, excludeUserId);
        }
        if (userMapper.selectCount(query) > 0) {
            throw new BusinessException("邮箱已被使用");
        }
    }

    /**
     * 加载可变更的普通用户，拒绝管理员账户。
     *
     * @param id 用户标识
     * @return 存在且非 ADMIN 的用户实体
     */
    private SysUser requireMutableUser(long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (userMapper.isAdmin(id) > 0) {
            throw new BusinessException("ADMIN 账号不可禁用、重置或改动");
        }
        return user;
    }

    /**
     * 校验角色标识列表非空且均为可分配的非管理员角色。
     *
     * @param roleIds 请求绑定的角色标识
     * @return 去重后的有效角色标识列表
     */
    private List<Long> validateRoles(List<Long> roleIds) {
        List<Long> roles = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        if (roles.isEmpty() || userMapper.countAssignableRoles(roles) != roles.size()) {
            throw new BusinessException("用户必须绑定至少一个有效的非管理员角色");
        }
        return roles;
    }

    /**
     * 原子替换用户的角色绑定关系。
     *
     * @param userId  用户标识
     * @param roleIds 新的角色标识列表
     */
    private void replaceRoles(long userId, List<Long> roleIds) {
        userMapper.deleteRoles(userId);
        if (userMapper.insertRoles(userId, roleIds) != roleIds.size()) {
            throw new BusinessException("用户角色绑定不完整");
        }
    }

    /**
     * 校验当前用户为管理员，否则拒绝访问。
     */
    private void requireAdmin() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可管理用户");
        }
    }

    /**
     * 将用户实体及其角色、权限组装为管理视图。
     *
     * @param user 用户实体
     * @return 包含实时角色与权限的用户视图
     */
    private UserView view(SysUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                Boolean.TRUE.equals(user.getEnabled()), isActivated(user),
                Boolean.TRUE.equals(user.getTotpEnabled()),
                userMapper.findRoleIds(user.getId()), userMapper.findRoles(user.getId()),
                userMapper.findPermissions(user.getId()));
    }

    /**
     * 用户管理视图。
     *
     * @param id          用户标识
     * @param username    不可变登录用户名
     * @param displayName 展示名称
     * @param email       邮箱
     * @param enabled     是否启用
     * @param activated   是否已激活
     * @param totpEnabled 是否已启用双因子
     * @param roleIds     已绑定角色标识列表
     * @param roles       当前启用角色编码列表
     * @param permissions 当前启用角色的功能权限并集
     */
    public record UserView(@JsonSerialize(using = ToStringSerializer.class) long id,
                           String username, String displayName, String email, boolean enabled,
                           boolean activated, boolean totpEnabled,
                           @JsonSerialize(contentUsing = ToStringSerializer.class) List<Long> roleIds,
                           List<String> roles, List<String> permissions) {
    }

    /**
     * 用户目录项。
     *
     * @param id          用户标识
     * @param username    登录用户名
     * @param displayName 展示名称
     */
    public record UserDirectoryItem(@JsonSerialize(using = ToStringSerializer.class) long id,
                                    String username, String displayName, String email) {
    }

    /**
     * 设密页预览。
     *
     * @param username    用户名
     * @param displayName 展示名称
     * @param purpose     令牌用途
     */
    public record SetupPreview(String username, String displayName, String purpose) {
    }
}
