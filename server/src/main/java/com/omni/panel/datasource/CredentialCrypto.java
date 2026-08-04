package com.omni.panel.datasource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.omni.panel.common.BusinessException;

/**
 * 使用 AES-256-GCM 加密和解密数据源凭据。
 *
 * <p>每次加密生成独立随机 IV，并将 IV 与密文一并编码存储。</p>
 */
@Component
public class CredentialCrypto {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCrypto(@Value("${omni.crypto.master-key}") String masterKey) {
        byte[] decoded = Base64.getDecoder().decode(masterKey);
        if (decoded.length != 32) {
            throw new IllegalArgumentException("凭据主密钥必须是 Base64 编码的 32 字节密钥");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    /**
     * 加密明文凭据。
     *
     * @param plaintext 明文凭据
     * @return 包含随机 IV 的 Base64 密文
     * @throws BusinessException 加密算法执行失败时抛出
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(500, "数据源凭据加密失败");
        }
    }

    /**
     * 解密由 {@link #encrypt(String)} 生成的凭据密文。
     *
     * @param encrypted Base64 密文
     * @return 明文凭据
     * @throws BusinessException 密文格式无效、认证失败或解密算法执行失败时抛出
     */
    public String decrypt(String encrypted) {
        try {
            byte[] payload = Base64.getDecoder().decode(encrypted);
            if (payload.length <= IV_LENGTH) {
                throw new GeneralSecurityException("密文长度不合法");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[payload.length - IV_LENGTH];
            ByteBuffer.wrap(payload).get(iv).get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BusinessException(500, "数据源凭据解密失败");
        }
    }
}
