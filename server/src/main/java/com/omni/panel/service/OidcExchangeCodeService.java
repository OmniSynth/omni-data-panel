package com.omni.panel.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;

/**
 * OIDC 登录成功后向前端发放的一次性 JWT 兑换码（进程内，TTL 2 分钟）。
 */
@Service
public class OidcExchangeCodeService {
    private final Cache<String, String> codes = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(10_000)
            .build();
    private final SecureRandom random = new SecureRandom();

    /**
     * 签发一次性兑换码，绑定访问令牌。
     *
     * @param accessToken Omni 访问令牌
     * @return 兑换码
     */
    public String issue(String accessToken) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, accessToken);
        return code;
    }

    /**
     * 消费兑换码并返回访问令牌；码无效或已用则失败。
     *
     * @param code 一次性兑换码
     * @return 访问令牌
     */
    public String consume(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(400, "兑换码无效或已过期");
        }
        String token = codes.asMap().remove(code.trim());
        if (token == null || token.isBlank()) {
            throw new BusinessException(400, "兑换码无效或已过期");
        }
        return token;
    }

    /** 测试辅助：窥视码是否仍存在。 */
    Optional<String> peek(String code) {
        return Optional.ofNullable(codes.getIfPresent(code));
    }
}
