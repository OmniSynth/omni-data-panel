package com.omni.panel.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
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
    /** JWT 内锁定参数条数上限，防止超大 claim。 */
    public static final int MAX_LOCKED_PARAMETERS = 32;

    private final SecretKey key;
    private final SettingService settingService;

    /**
     * 注入 JWT 签名密钥与系统设置服务。
     *
     * @param properties     安全配置（含 JWT 密钥）
     * @param settingService 系统设置服务
     */
    public EmbedTokenService(SecurityProperties properties, SettingService settingService) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.jwtSecret()));
        this.settingService = settingService;
    }

    /**
     * 签发嵌入令牌（无锁定参数）。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @return 已签名令牌
     */
    public String create(String resourceType, long resourceId) {
        return create(resourceType, resourceId, null);
    }

    /**
     * 签发嵌入令牌；可选将仪表盘锁定参数写入 JWT claims。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param parameters   锁定参数；空或 {@code null} 时不写入 claim
     * @return 已签名令牌
     */
    public String create(String resourceType, long resourceId, Map<String, Object> parameters) {
        requireEmbedEnabled();
        String type = normalize(resourceType);
        Map<String, Object> locked = sanitizeParameters(parameters);
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject("embed")
                .claim("typ", "embed")
                .claim("resourceType", type)
                .claim("resourceId", resourceId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(DEFAULT_TTL)));
        if (!locked.isEmpty()) {
            builder.claim("parameters", locked);
        }
        return builder.signWith(key).compact();
    }

    /**
     * 解析并校验嵌入令牌。
     *
     * @param token 嵌入令牌
     * @return 资源声明（含可选锁定参数）
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
            return new EmbedClaims(normalize(type), ((Number) resourceId).longValue(),
                    readParametersClaim(claims.get("parameters")));
        } catch (BusinessException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException | ClassCastException exception) {
            throw new BusinessException(401, "嵌入令牌无效或已过期");
        }
    }

    /**
     * 校验锁定参数仅包含仪表盘已声明的参数 id，且不超过条数上限。
     *
     * @param parameters 请求中的锁定参数
     * @param allowedIds 仪表盘参数 id 集合
     * @return 规范化后的锁定参数（可能为空）
     */
    public Map<String, Object> requireAllowedParameters(Map<String, Object> parameters, Set<String> allowedIds) {
        Map<String, Object> locked = sanitizeParameters(parameters);
        if (locked.isEmpty()) {
            return locked;
        }
        Set<String> allowed = allowedIds == null ? Set.of() : allowedIds;
        for (String key : locked.keySet()) {
            if (!allowed.contains(key)) {
                throw new BusinessException(400, "锁定参数不存在：" + key);
            }
        }
        return locked;
    }

    /**
     * 规范化锁定参数：拷贝、校验条数；{@code null}/空返回空 Map。
     *
     * @param parameters 原始参数
     * @return 不可变拷贝
     */
    public Map<String, Object> sanitizeParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        if (parameters.size() > MAX_LOCKED_PARAMETERS) {
            throw new BusinessException(400, "锁定参数最多 " + MAX_LOCKED_PARAMETERS + " 项");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new BusinessException(400, "锁定参数名不能为空");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
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

    private Map<String, Object> readParametersClaim(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new BusinessException(401, "嵌入令牌无效");
        }
        if (map.isEmpty()) {
            return Map.of();
        }
        if (map.size() > MAX_LOCKED_PARAMETERS) {
            throw new BusinessException(401, "嵌入令牌无效");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                throw new BusinessException(401, "嵌入令牌无效");
            }
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 嵌入令牌声明。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param parameters   锁定参数（不可变；无锁定时为空 Map）
     */
    public record EmbedClaims(String resourceType, long resourceId, Map<String, Object> parameters) {
        public EmbedClaims {
            parameters = parameters == null || parameters.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        }
    }
}
