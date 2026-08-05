package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.CredentialCrypto;
import com.omni.panel.entity.SysUser;
import com.omni.panel.entity.TotpBackupCodeEntity;
import com.omni.panel.mapper.TotpBackupCodeMapper;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.service.SettingService;
import com.omni.panel.service.TotpService;

class TotpServiceTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final TotpBackupCodeMapper backupCodeMapper = mock(TotpBackupCodeMapper.class);
    private final SettingService settingService = mock(SettingService.class);
    private CredentialCrypto crypto;
    private TotpService service;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        crypto = new CredentialCrypto(java.util.Base64.getEncoder().encodeToString(key));
        when(settingService.getOrDefault("site.name")).thenReturn("测试站点");
        service = new TotpService(userMapper, backupCodeMapper, crypto, settingService);
    }

    @Test
    void 开始绑定写入加密pending密钥并返回otpauth() {
        SysUser user = user(false, null, null);
        when(userMapper.selectById(7L)).thenReturn(user);

        TotpService.SetupInfo setup = service.beginSetup(7L);

        assertThat(setup.secret()).isNotBlank();
        assertThat(setup.otpauthUri()).startsWith("otpauth://totp/");
        assertThat(setup.otpauthUri()).contains("secret=" + setup.secret());
        assertThat(user.getTotpPendingSecret()).isNotBlank();
        assertThat(crypto.decrypt(user.getTotpPendingSecret())).isEqualTo(setup.secret());
        verify(userMapper).updateById(user);
    }

    @Test
    void 正确验证码可确认绑定并生成备用码() throws Exception {
        String secret = new dev.samstevens.totp.secret.DefaultSecretGenerator().generate();
        SysUser user = user(false, null, crypto.encrypt(secret));
        when(userMapper.selectById(7L)).thenReturn(user);
        when(backupCodeMapper.delete(any())).thenReturn(10);
        when(backupCodeMapper.insert(any(TotpBackupCodeEntity.class))).thenReturn(1);

        String code = service.currentCodeForTest(secret);
        var backupCodes = service.confirmSetup(7L, code);

        assertThat(backupCodes).hasSize(10);
        assertThat(user.getTotpEnabled()).isTrue();
        assertThat(user.getTotpPendingSecret()).isNull();
        assertThat(crypto.decrypt(user.getTotpSecret())).isEqualTo(secret);
        ArgumentCaptor<TotpBackupCodeEntity> captor = ArgumentCaptor.forClass(TotpBackupCodeEntity.class);
        verify(backupCodeMapper, org.mockito.Mockito.times(10)).insert(captor.capture());
        assertThat(captor.getAllValues()).allMatch(row -> row.getCodeHash() != null && row.getUsedAt() == null);
    }

    @Test
    void 错误验证码拒绝确认绑定() {
        String secret = new dev.samstevens.totp.secret.DefaultSecretGenerator().generate();
        SysUser user = user(false, null, crypto.encrypt(secret));
        when(userMapper.selectById(7L)).thenReturn(user);

        assertThatThrownBy(() -> service.confirmSetup(7L, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码错误");
    }

    @Test
    void 备用码哈希规范化大小写() {
        assertThat(TotpService.hashBackupCode("ab12cd34"))
                .isEqualTo(TotpService.hashBackupCode("AB12CD34"));
    }

    private static SysUser user(boolean enabled, String secret, String pending) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("tester");
        user.setTotpEnabled(enabled);
        user.setTotpSecret(secret);
        user.setTotpPendingSecret(pending);
        return user;
    }
}
