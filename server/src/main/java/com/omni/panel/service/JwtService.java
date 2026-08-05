package com.omni.panel.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.SecurityProperties;

/**
 * 负责签发和校验系统使用的 JWT 访问令牌与 MFA 中间令牌。
 */
@Service
public class JwtService {
    public static final String TYP_ACCESS = "access";
    public static final String TYP_MFA_PENDING = "mfa_pending";
    private static final Duration MFA_PENDING_TTL = Duration.ofMinutes(5);

    private final SecurityProperties properties;
    private final SecretKey key;

    /**
     * 从安全配置加载 JWT 签名密钥。
     *
     * @param properties 安全相关配置
     */
    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.jwtSecret()));
    }

    /**
     * 为用户签发正式访问令牌（含 jti，用于会话并发控制）。
     *
     * @param userId   用户标识，写入令牌主题
     * @param username 用户名，写入令牌声明
     * @return 访问令牌与会话标识
     */
    public AccessToken createAccess(long userId, String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.jwtExpiration());
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .id(jti)
                .subject(Long.toString(userId))
                .claim("username", username)
                .claim("typ", TYP_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new AccessToken(token, jti, expiresAt);
    }

    /**
     * 为用户签发正式访问令牌字符串。
     *
     * @param userId   用户标识
     * @param username 用户名
     * @return JWT 字符串
     */
    public String create(long userId, String username) {
        return createAccess(userId, username).token();
    }

    /**
     * 签发仅用于完成 MFA 校验的短期中间令牌。
     *
     * @param userId   用户标识
     * @param username 用户名
     * @return MFA 中间令牌
     */
    public String createMfaPending(long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("username", username)
                .claim("typ", TYP_MFA_PENDING)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(MFA_PENDING_TTL)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验访问令牌的签名与有效期并读取声明。
     *
     * @param token JWT 字符串
     * @return 校验通过的令牌声明
     * @throws io.jsonwebtoken.JwtException 令牌无效、签名错误或已过期时抛出
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 判断声明是否可作为会话访问令牌（兼容无 typ 的旧令牌）。
     *
     * @param claims 令牌声明
     * @return 可作为访问令牌时返回 {@code true}
     */
    public boolean isAccessToken(Claims claims) {
        Object typ = claims.get("typ");
        return typ == null || TYP_ACCESS.equals(String.valueOf(typ));
    }

    /**
     * 解析 MFA 中间令牌并返回用户 ID。
     *
     * @param token MFA 中间令牌
     * @return 用户标识
     */
    public long requireMfaPendingUserId(String token) {
        try {
            Claims claims = parse(token);
            if (!TYP_MFA_PENDING.equals(String.valueOf(claims.get("typ")))) {
                throw new BusinessException(401, "MFA 令牌无效");
            }
            return Long.parseLong(claims.getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(401, "MFA 令牌无效或已过期");
        }
    }

    /**
     * 访问令牌签发结果。
     *
     * @param token     JWT 字符串
     * @param jti       会话标识
     * @param expiresAt 过期时间
     */
    public record AccessToken(String token, String jti, Instant expiresAt) {
    }
}
