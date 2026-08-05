package com.omni.panel.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.CredentialCrypto;
import com.omni.panel.entity.SysUser;
import com.omni.panel.entity.TotpBackupCodeEntity;
import com.omni.panel.mapper.TotpBackupCodeMapper;
import com.omni.panel.mapper.UserMapper;

/**
 * 管理 TOTP 绑定、校验与一次性备用码。
 */
@Service
public class TotpService {
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_LENGTH = 8;
    private static final String BACKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserMapper userMapper;
    private final TotpBackupCodeMapper backupCodeMapper;
    private final CredentialCrypto crypto;
    private final SettingService settingService;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param userMapper       用户持久化
     * @param backupCodeMapper 备用码持久化
     * @param crypto           密钥加解密
     * @param settingService   站点名等设置（otpauth issuer）
     */
    public TotpService(UserMapper userMapper, TotpBackupCodeMapper backupCodeMapper,
                       CredentialCrypto crypto, SettingService settingService) {
        this.userMapper = userMapper;
        this.backupCodeMapper = backupCodeMapper;
        this.crypto = crypto;
        this.settingService = settingService;
        CodeGenerator generator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(generator, new SystemTimeProvider());
        verifier.setTimePeriod(30);
        verifier.setAllowedTimePeriodDiscrepancy(1);
        this.codeVerifier = verifier;
    }

    /**
     * 查询用户是否已启用 TOTP。
     *
     * @param userId 用户标识
     * @return 已启用时返回 {@code true}
     */
    public boolean isEnabled(long userId) {
        SysUser user = requireUser(userId);
        return Boolean.TRUE.equals(user.getTotpEnabled());
    }

    /**
     * 开始绑定：生成 pending 密钥并返回明文与 otpauth URI。
     *
     * @param userId 用户标识
     * @return 绑定信息
     */
    @Transactional
    public SetupInfo beginSetup(long userId) {
        SysUser user = requireUser(userId);
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException("双因子认证已启用");
        }
        String secret = secretGenerator.generate();
        user.setTotpPendingSecret(crypto.encrypt(secret));
        userMapper.updateById(user);
        return new SetupInfo(secret, buildOtpAuthUri(user.getUsername(), secret));
    }

    /**
     * 用验证码确认绑定并生成备用码。
     *
     * @param userId 用户标识
     * @param code   动态验证码
     * @return 一次性明文备用码列表
     */
    @Transactional
    public List<String> confirmSetup(long userId, String code) {
        SysUser user = requireUser(userId);
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException("双因子认证已启用");
        }
        String pending = user.getTotpPendingSecret();
        if (pending == null || pending.isBlank()) {
            throw new BusinessException("请先开始绑定双因子认证");
        }
        String secret = crypto.decrypt(pending);
        if (!verifyTotp(secret, code)) {
            throw new BusinessException("验证码错误");
        }
        user.setTotpSecret(pending);
        user.setTotpPendingSecret(null);
        user.setTotpEnabled(true);
        userMapper.updateById(user);
        return replaceBackupCodes(userId);
    }

    /**
     * 校验登录第二步：TOTP 或未使用的备用码。
     *
     * @param userId 用户标识
     * @param code   验证码或备用码
     * @return 校验通过时返回 {@code true}
     */
    @Transactional
    public boolean verifyLoginCode(long userId, String code) {
        SysUser user = requireUser(userId);
        if (!Boolean.TRUE.equals(user.getTotpEnabled()) || user.getTotpSecret() == null) {
            throw new BusinessException(401, "双因子认证未启用");
        }
        String normalized = normalizeCode(code);
        if (normalized.isEmpty()) {
            return false;
        }
        if (verifyTotp(crypto.decrypt(user.getTotpSecret()), normalized)) {
            return true;
        }
        return consumeBackupCode(userId, normalized);
    }

    /**
     * 关闭双因子：需密码已在外层校验，此处校验 TOTP/备用码后清空状态。
     *
     * @param userId 用户标识
     * @param code   验证码或备用码
     */
    @Transactional
    public void disable(long userId, String code) {
        SysUser user = requireUser(userId);
        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException("双因子认证未启用");
        }
        if (!verifyLoginCode(userId, code)) {
            throw new BusinessException("验证码错误");
        }
        clearMfa(userId);
    }

    /**
     * 管理员强制清除用户 MFA。
     *
     * @param userId 用户标识
     */
    @Transactional
    public void resetForUser(long userId) {
        requireUser(userId);
        clearMfa(userId);
    }

    /**
     * 关闭用户 TOTP 并删除全部备用码。
     *
     * @param userId 用户标识
     */
    private void clearMfa(long userId) {
        SysUser user = requireUser(userId);
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpPendingSecret(null);
        userMapper.updateById(user);
        backupCodeMapper.delete(Wrappers.<TotpBackupCodeEntity>lambdaQuery()
                .eq(TotpBackupCodeEntity::getUserId, userId));
    }

    /**
     * 删除旧备用码并生成一批新的；库中存哈希，返回明文供用户保存一次。
     *
     * @param userId 用户标识
     * @return 明文备用码列表
     */
    private List<String> replaceBackupCodes(long userId) {
        backupCodeMapper.delete(Wrappers.<TotpBackupCodeEntity>lambdaQuery()
                .eq(TotpBackupCodeEntity::getUserId, userId));
        List<String> plain = new ArrayList<>(BACKUP_CODE_COUNT);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = randomBackupCode();
            plain.add(code);
            TotpBackupCodeEntity row = new TotpBackupCodeEntity();
            row.setUserId(userId);
            row.setCodeHash(hashBackupCode(code));
            row.setCreatedAt(now);
            backupCodeMapper.insert(row);
        }
        return plain;
    }

    /**
     * 校验并消费一条未使用的备用码。
     *
     * @param userId 用户标识
     * @param code   明文备用码
     * @return 消费成功时返回 {@code true}
     */
    private boolean consumeBackupCode(long userId, String code) {
        String hash = hashBackupCode(code);
        TotpBackupCodeEntity row = backupCodeMapper.selectOne(Wrappers.<TotpBackupCodeEntity>lambdaQuery()
                .eq(TotpBackupCodeEntity::getUserId, userId)
                .eq(TotpBackupCodeEntity::getCodeHash, hash)
                .isNull(TotpBackupCodeEntity::getUsedAt)
                .last("LIMIT 1"));
        if (row == null) {
            return false;
        }
        row.setUsedAt(LocalDateTime.now());
        backupCodeMapper.updateById(row);
        return true;
    }

    /**
     * 校验 6 位 TOTP 动态码（允许相邻时间窗口）。
     *
     * @param secret Base32 明文密钥
     * @param code   用户输入
     * @return 校验通过时返回 {@code true}
     */
    private boolean verifyTotp(String secret, String code) {
        String digits = code.replaceAll("\\s+", "");
        if (!digits.matches("\\d{6}")) {
            return false;
        }
        try {
            return codeVerifier.isValidCode(secret, digits);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 组装 Authenticator 可扫描的 otpauth URI。
     *
     * @param username 登录名（写入 label）
     * @param secret   Base32 明文密钥
     * @return otpauth://totp/... URI
     */
    private String buildOtpAuthUri(String username, String secret) {
        String issuer = settingService.getOrDefault("site.name");
        if (issuer == null || issuer.isBlank()) {
            issuer = "Omni Data Panel";
        }
        String label = URLEncoder.encode(issuer + ":" + username, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String issuerParam = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + issuerParam
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * 生成单条随机备用码明文。
     *
     * @return 固定长度易读字符码
     */
    private String randomBackupCode() {
        StringBuilder builder = new StringBuilder(BACKUP_CODE_LENGTH);
        for (int i = 0; i < BACKUP_CODE_LENGTH; i++) {
            builder.append(BACKUP_ALPHABET.charAt(secureRandom.nextInt(BACKUP_ALPHABET.length())));
        }
        return builder.toString();
    }

    /**
     * 规范化备用码：去空白并转大写。
     *
     * @param code 原始输入
     * @return 规范化结果；{@code null} 时返回空串
     */
    private static String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * 计算备用码的 SHA-256 十六进制哈希（入库用）。
     *
     * @param code 明文备用码
     * @return 小写十六进制摘要
     */
    public static String hashBackupCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalizeCode(code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /**
     * 按 ID 加载用户；不存在时抛出 404。
     *
     * @param userId 用户标识
     * @return 用户实体
     */
    private SysUser requireUser(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    /**
     * 绑定开始结果。
     *
     * @param secret     Base32 明文密钥
     * @param otpauthUri Authenticator 扫描 URI
     */
    public record SetupInfo(String secret, String otpauthUri) {
    }

    /**
     * 生成当前时间窗口的验证码（测试与调试用）。
     *
     * @param secret Base32 密钥
     * @return 6 位动态码
     */
    public String currentCodeForTest(String secret) throws CodeGenerationException {
        return new DefaultCodeGenerator(HashingAlgorithm.SHA1)
                .generate(secret, new SystemTimeProvider().getTime() / 30);
    }
}
