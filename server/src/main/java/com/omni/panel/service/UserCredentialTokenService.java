package com.omni.panel.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.UserCredentialTokenEntity;
import com.omni.panel.mapper.UserCredentialTokenMapper;

/**
 * 签发与核销用户激活 / 重置密码令牌。
 */
@Service
public class UserCredentialTokenService {
    private static final Duration ACTIVATE_TTL = Duration.ofHours(48);
    private static final Duration RESET_TTL = Duration.ofHours(24);

    private final UserCredentialTokenMapper mapper;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 注入凭证令牌持久化依赖。
     *
     * @param mapper 凭证令牌持久化
     */
    public UserCredentialTokenService(UserCredentialTokenMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 为用户签发新令牌，并使同用途未使用令牌失效。
     *
     * @param userId  用户标识
     * @param purpose 用途（激活或重置）
     * @return 原始令牌（仅此时可见）
     */
    @Transactional
    public String issue(long userId, String purpose) {
        invalidateOpen(userId, purpose);
        String raw = generateRawToken();
        LocalDateTime now = LocalDateTime.now();
        UserCredentialTokenEntity entity = new UserCredentialTokenEntity();
        entity.setUserId(userId);
        entity.setTokenHash(hash(raw));
        entity.setPurpose(purpose);
        entity.setExpiresAt(now.plus(ttl(purpose)));
        entity.setCreatedAt(now);
        mapper.insert(entity);
        return raw;
    }

    /**
     * 校验令牌有效性（不消费）。
     *
     * @param rawToken 原始令牌
     * @return 令牌实体
     */
    public UserCredentialTokenEntity requireValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException("链接无效或已过期");
        }
        UserCredentialTokenEntity entity = mapper.selectOne(new QueryWrapper<UserCredentialTokenEntity>()
                .eq("token_hash", hash(rawToken.trim())));
        if (entity == null || entity.getUsedAt() != null
                || entity.getExpiresAt() == null || entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("链接无效或已过期");
        }
        return entity;
    }

    /**
     * 校验并消费令牌。
     *
     * @param rawToken        原始令牌
     * @param expectedPurpose 期望用途
     * @return 已消费的令牌实体
     */
    @Transactional
    public UserCredentialTokenEntity consume(String rawToken, String expectedPurpose) {
        UserCredentialTokenEntity entity = requireValid(rawToken);
        if (!expectedPurpose.equals(entity.getPurpose())) {
            throw new BusinessException("链接用途不匹配");
        }
        entity.setUsedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return entity;
    }

    /**
     * 使同用户同用途的未使用令牌全部失效。
     *
     * @param userId  用户标识
     * @param purpose 用途
     */
    private void invalidateOpen(long userId, String purpose) {
        mapper.update(null, new UpdateWrapper<UserCredentialTokenEntity>()
                .eq("user_id", userId)
                .eq("purpose", purpose)
                .isNull("used_at")
                .set("used_at", LocalDateTime.now()));
    }

    /**
     * 按用途返回令牌有效期。
     *
     * @param purpose 用途
     * @return 有效期
     */
    private static Duration ttl(String purpose) {
        if (UserCredentialTokenEntity.PURPOSE_ACTIVATE.equals(purpose)) {
            return ACTIVATE_TTL;
        }
        return RESET_TTL;
    }

    /**
     * 生成 URL 安全的随机原始令牌。
     *
     * @return 原始令牌字符串
     */
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 对原始令牌做 SHA-256 哈希（十六进制）。
     *
     * @param raw 原始令牌
     * @return 哈希值
     */
    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
