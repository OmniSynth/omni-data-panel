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
 * 订阅 PDF 渲染用的短期打印令牌（不依赖嵌入开关）。
 */
@Service
public class SubscriptionPrintTokenService {
    private static final Set<String> TYPES = Set.of("DASHBOARD");
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    private final SecretKey key;

    public SubscriptionPrintTokenService(SecurityProperties properties) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.jwtSecret()));
    }

    /**
     * 签发仪表盘打印令牌。
     *
     * @param dashboardId 仪表盘标识
     * @return 已签名令牌
     */
    public String create(long dashboardId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("subscription-print")
                .claim("typ", "subscription-print")
                .claim("resourceType", "DASHBOARD")
                .claim("resourceId", dashboardId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(DEFAULT_TTL)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验打印令牌。
     *
     * @param token 打印令牌
     * @return 仪表盘标识
     */
    public long parseDashboardId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!"subscription-print".equals(claims.get("typ"))) {
                throw new BusinessException(401, "打印令牌无效");
            }
            String type = String.valueOf(claims.get("resourceType"));
            if (!TYPES.contains(type == null ? "" : type.toUpperCase())) {
                throw new BusinessException(401, "打印令牌无效");
            }
            Object resourceId = claims.get("resourceId");
            if (resourceId == null) {
                throw new BusinessException(401, "打印令牌无效");
            }
            return ((Number) resourceId).longValue();
        } catch (JwtException | IllegalArgumentException | ClassCastException exception) {
            throw new BusinessException(401, "打印令牌无效或已过期");
        }
    }
}
