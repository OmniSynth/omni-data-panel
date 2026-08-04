package com.omni.panel.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.SecurityProperties;

/**
 * 签发与校验含资源声明的短期嵌入令牌。
 */
@Service
public class EmbedTokenService {
    private static final Set<String> TYPES = Set.of("DASHBOARD", "QUESTION");
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);
    private final SecretKey key;
    private final SettingService settingService;

    public EmbedTokenService(SecurityProperties properties, SettingService settingService) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.jwtSecret()));
        this.settingService = settingService;
    }

    /**
     * 签发嵌入令牌。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @return 已签名令牌
     */
    public String create(String resourceType, long resourceId) {
        requireEmbedEnabled();
        String type = normalize(resourceType);
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("embed")
                .claim("typ", "embed")
                .claim("resourceType", type)
                .claim("resourceId", resourceId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(DEFAULT_TTL)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验嵌入令牌。
     *
     * @param token 嵌入令牌
     * @return 资源声明
     */
    public EmbedClaims parse(String token) {
        requireEmbedEnabled();
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!"embed".equals(claims.get("typ"))) {
                throw new BusinessException(401, "嵌入令牌无效");
            }
            String type = String.valueOf(claims.get("resourceType"));
            Object resourceId = claims.get("resourceId");
            if (resourceId == null) {
                throw new BusinessException(401, "嵌入令牌无效");
            }
            return new EmbedClaims(normalize(type), ((Number) resourceId).longValue());
        } catch (JwtException | IllegalArgumentException | ClassCastException exception) {
            throw new BusinessException(401, "嵌入令牌无效或已过期");
        }
    }

    /**
     * 校验嵌入功能已启用，否则拒绝签发或解析令牌。
     */
    private void requireEmbedEnabled() {
        if (!settingService.embedEnabled()) {
            throw new BusinessException(403, "嵌入功能已关闭");
        }
    }

    /**
     * 规范化资源类型编码并校验合法性。
     *
     * @param resourceType 资源类型
     * @return 大写的 DASHBOARD 或 QUESTION
     */
    private String normalize(String resourceType) {
        String type = resourceType == null ? "" : resourceType.toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BusinessException("嵌入仅支持 DASHBOARD 或 QUESTION");
        }
        return type;
    }

    /**
     * 嵌入令牌声明。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     */
    public record EmbedClaims(String resourceType, long resourceId) {
    }
}
