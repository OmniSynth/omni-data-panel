package com.omni.panel.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;

/**
 * 登录挑战与 HMAC 签名校验，防止登录请求重放。
 */
@Service
public class LoginChallengeService {
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);
    private static final int SIGN_KEY_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    /**
     * 签发一次性登录挑战。
     *
     * @return 挑战信息（含前端签名密钥）
     */
    public ChallengeView issue() {
        purgeExpired();
        String challengeId = UUID.randomUUID().toString().replace("-", "");
        String nonce = UUID.randomUUID().toString().replace("-", "");
        byte[] keyBytes = new byte[SIGN_KEY_BYTES];
        secureRandom.nextBytes(keyBytes);
        String signKey = HexFormat.of().formatHex(keyBytes);
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = Instant.now().plus(TTL).getEpochSecond();
        challenges.put(challengeId, new Challenge(nonce, signKey, issuedAt, expiresAt, false));
        return new ChallengeView(challengeId, nonce, issuedAt, expiresAt, signKey);
    }

    /**
     * 校验并消耗挑战：核对 nonce、时效与 HMAC 签名。
     *
     * @param challengeId 挑战标识
     * @param nonce       挑战 nonce
     * @param timestamp   客户端时间戳（秒）
     * @param username    用户名
     * @param password    密码明文
     * @param signature   十六进制 HMAC-SHA256
     */
    public void verifyAndConsume(String challengeId, String nonce, long timestamp,
                                 String username, String password, String signature) {
        purgeExpired();
        Challenge challenge = challenges.remove(challengeId);
        if (challenge == null || challenge.used()) {
            throw new BusinessException(401, "登录挑战无效或已使用");
        }
        long now = Instant.now().getEpochSecond();
        if (now > challenge.expiresAt()) {
            throw new BusinessException(401, "登录挑战已过期");
        }
        if (!challenge.nonce().equals(nonce)) {
            throw new BusinessException(401, "登录签名校验失败");
        }
        if (timestamp < challenge.issuedAt() - CLOCK_SKEW.getSeconds()
                || timestamp > challenge.expiresAt() + CLOCK_SKEW.getSeconds()) {
            throw new BusinessException(401, "登录时间戳无效");
        }
        String expected = sign(challenge.signKey(), username, password, nonce, timestamp);
        byte[] expectedBytes = HexFormat.of().parseHex(expected);
        byte[] actualBytes;
        try {
            actualBytes = HexFormat.of().parseHex(normalizeHex(signature));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(401, "登录签名校验失败");
        }
        if (expectedBytes.length != actualBytes.length
                || !MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new BusinessException(401, "登录签名校验失败");
        }
    }

    /**
     * 计算登录签名（与前端约定一致）。
     *
     * @param signKey   挑战签名密钥
     * @param username  用户名
     * @param password  密码
     * @param nonce     挑战 nonce
     * @param timestamp 时间戳秒
     * @return 小写十六进制 HMAC
     */
    public static String sign(String signKey, String username, String password, String nonce, long timestamp) {
        String payload = username + "\n" + password + "\n" + nonce + "\n" + timestamp;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HexFormat.of().parseHex(signKey), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(500, "登录签名计算失败");
        }
    }

    /**
     * 移除已过期的挑战条目。
     */
    private void purgeExpired() {
        long now = Instant.now().getEpochSecond();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    /**
     * 规范化十六进制字符串（去空白并转小写）。
     *
     * @param value 原始十六进制
     * @return 规范化后的字符串；{@code null} 时返回空串
     */
    private static String normalizeHex(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /**
     * 内存中的登录挑战状态。
     *
     * @param nonce     随机数
     * @param signKey   前端 HMAC 密钥（十六进制）
     * @param issuedAt  签发时间（秒）
     * @param expiresAt 过期时间（秒）
     * @param used      是否已消耗
     */
    private record Challenge(String nonce, String signKey, long issuedAt, long expiresAt, boolean used) {
    }

    /**
     * 登录挑战视图。
     *
     * @param challengeId 挑战标识
     * @param nonce       随机数
     * @param timestamp   签发时间（秒）
     * @param expiresAt   过期时间（秒）
     * @param signKey     前端 HMAC 密钥（十六进制）
     */
    public record ChallengeView(String challengeId, String nonce, long timestamp, long expiresAt, String signKey) {
    }
}
