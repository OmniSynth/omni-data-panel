package com.omni.panel.datasource;

import com.omni.panel.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialCryptoTest {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final CredentialCrypto crypto = new CredentialCrypto(KEY);

    @Test
    @DisplayName("AES-GCM 可正确加解密且每次密文不同")
    void encryptsAndDecrypts() {
        String first = crypto.encrypt("数据库密码");
        String second = crypto.encrypt("数据库密码");

        assertNotEquals(first, second);
        assertEquals("数据库密码", crypto.decrypt(first));
        assertEquals("数据库密码", crypto.decrypt(second));
    }

    @Test
    @DisplayName("AES-GCM 拒绝被篡改的密文")
    void rejectsTamperedCiphertext() {
        String encrypted = crypto.encrypt("secret");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";
        assertThrows(BusinessException.class, () -> crypto.decrypt(tampered));
    }
}
