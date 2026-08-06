package com.omni.panel.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.OidcProperties;
import com.omni.panel.entity.SysRoleEntity;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.RoleMapper;
import com.omni.panel.mapper.UserMapper;

/**
 * 将 OIDC 主体映射到本地用户：按 subject / 邮箱查找，否则 JIT 建号并赋予默认角色。
 */
@Service
public class OidcUserProvisioningService {
    public static final String AUTH_SOURCE_OIDC = "OIDC";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OidcProperties oidcProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OidcUserProvisioningService(UserMapper userMapper, RoleMapper roleMapper,
                                       PasswordEncoder passwordEncoder, OidcProperties oidcProperties) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.oidcProperties = oidcProperties;
    }

    /**
     * 解析或创建本地用户。
     *
     * @param oidcUser IdP 用户
     * @return 已启用的本地用户
     */
    @Transactional
    public SysUser provision(OidcUser oidcUser) {
        String subject = blankToNull(oidcUser.getSubject());
        if (subject == null) {
            throw new BusinessException(401, "OIDC 主体缺少 subject");
        }
        String email = normalizeEmail(firstNonBlank(oidcUser.getEmail(),
                stringClaim(oidcUser, "preferred_username")));
        SysUser bySubject = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getIdpSubject, subject));
        if (bySubject != null) {
            return requireEnabled(syncProfile(bySubject, oidcUser, email));
        }
        if (email != null) {
            SysUser byEmail = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getEmail, email));
            if (byEmail != null) {
                byEmail.setIdpSubject(subject);
                if (byEmail.getAuthSource() == null || byEmail.getAuthSource().isBlank()) {
                    byEmail.setAuthSource(AUTH_SOURCE_OIDC);
                }
                return requireEnabled(syncProfile(byEmail, oidcUser, email));
            }
        }
        return requireEnabled(createUser(oidcUser, subject, email));
    }

    private SysUser createUser(OidcUser oidcUser, String subject, String email) {
        if (email == null) {
            throw new BusinessException(401, "OIDC 主体缺少可用邮箱，无法自动建号");
        }
        long roleId = resolveDefaultRoleId();
        String username = uniqueUsername(firstNonBlank(
                stringClaim(oidcUser, "preferred_username"),
                emailLocalPart(email),
                "oidc_user"));
        String displayName = firstNonBlank(oidcUser.getFullName(), oidcUser.getPreferredUsername(), username);
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(randomSecret()));
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setEnabled(true);
        user.setActivated(true);
        user.setActivatedAt(LocalDateTime.now());
        user.setTotpEnabled(false);
        user.setAuthSource(AUTH_SOURCE_OIDC);
        user.setIdpSubject(subject);
        userMapper.insert(user);
        userMapper.insertRoles(user.getId(), List.of(roleId));
        return user;
    }

    private SysUser syncProfile(SysUser user, OidcUser oidcUser, String email) {
        boolean dirty = false;
        if (user.getIdpSubject() == null || user.getIdpSubject().isBlank()) {
            user.setIdpSubject(oidcUser.getSubject());
            dirty = true;
        }
        if (email != null && (user.getEmail() == null || user.getEmail().isBlank())) {
            user.setEmail(email);
            dirty = true;
        }
        String displayName = firstNonBlank(oidcUser.getFullName(), oidcUser.getPreferredUsername(), null);
        if (displayName != null && (user.getDisplayName() == null || user.getDisplayName().isBlank())) {
            user.setDisplayName(displayName);
            dirty = true;
        }
        if (!Boolean.TRUE.equals(user.getActivated())) {
            user.setActivated(true);
            user.setActivatedAt(LocalDateTime.now());
            dirty = true;
        }
        if (dirty) {
            userMapper.updateById(user);
        }
        return user;
    }

    private SysUser requireEnabled(SysUser user) {
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(403, "用户已禁用");
        }
        return user;
    }

    private long resolveDefaultRoleId() {
        String code = oidcProperties.defaultRoleCode();
        SysRoleEntity role = roleMapper.selectOne(Wrappers.<SysRoleEntity>lambdaQuery()
                .eq(SysRoleEntity::getCode, code)
                .eq(SysRoleEntity::getEnabled, true));
        if (role == null || "ADMIN".equalsIgnoreCase(role.getCode())) {
            throw new BusinessException(500, "OIDC 默认角色不可用: " + code);
        }
        return role.getId();
    }

    private String uniqueUsername(String preferred) {
        String base = sanitizeUsername(preferred);
        if (base.isBlank()) {
            base = "oidc_user";
        }
        String candidate = base;
        int suffix = 0;
        while (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, candidate)) > 0) {
            suffix += 1;
            candidate = base + "_" + suffix;
            if (candidate.length() > 64) {
                candidate = base.substring(0, Math.max(1, 64 - ("_" + suffix).length())) + "_" + suffix;
            }
        }
        return candidate;
    }

    private static String sanitizeUsername(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int at = value.indexOf('@');
        if (at > 0) {
            value = value.substring(0, at);
        }
        value = value.replaceAll("[^a-z0-9._-]", "_");
        if (value.length() > 48) {
            value = value.substring(0, 48);
        }
        return value;
    }

    private static String emailLocalPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        String value = blankToNull(email);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String stringClaim(OidcUser user, String name) {
        Object value = user.getClaims().get(name);
        return value == null ? null : blankToNull(String.valueOf(value));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
