package com.omni.panel.service;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import com.omni.panel.config.SecurityProperties;

/**
 * 负责签发和校验系统使用的 JWT 访问令牌。
 */
@Service
public class JwtService {
    private final SecurityProperties properties;
    private final SecretKey key;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.jwtSecret()));
    }

    /**
     * 为用户签发带用户名声明和配置有效期的访问令牌。
     *
     * @param userId   用户标识，写入令牌主题
     * @param username 用户名，写入令牌声明
     * @return 已签名的 JWT 字符串
     */
    public String create(long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.jwtExpiration())))
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
}
