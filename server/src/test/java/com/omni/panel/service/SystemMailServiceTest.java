package com.omni.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.CredentialCrypto;
import com.omni.panel.subscription.SubscriptionProperties;

class SystemMailServiceTest {
    private final SettingService settingService = mock(SettingService.class);
    private final CredentialCrypto crypto = mock(CredentialCrypto.class);
    private final SubscriptionProperties properties = new SubscriptionProperties();

    @Test
    void 库配置齐全时ready为真() {
        when(settingService.getOrDefault(SettingService.MAIL_HOST)).thenReturn("smtp.example.com");
        when(settingService.getOrDefault(SettingService.MAIL_FROM)).thenReturn("noreply@example.com");
        when(settingService.getOrDefault(SettingService.MAIL_PORT)).thenReturn("587");
        when(settingService.getOrDefault(SettingService.MAIL_USERNAME)).thenReturn("user");
        when(settingService.getOrDefault(SettingService.MAIL_SMTP_AUTH)).thenReturn("true");
        when(settingService.getOrDefault(SettingService.MAIL_SMTP_STARTTLS)).thenReturn("true");
        when(settingService.get(SettingService.MAIL_PASSWORD)).thenReturn("cipher");
        when(crypto.decrypt("cipher")).thenReturn("secret");

        SystemMailService service = newService("", "");
        assertThat(service.ready()).isTrue();
        assertThat(service.from()).isEqualTo("noreply@example.com");
    }

    @Test
    void 库未配时回退环境变量() {
        when(settingService.getOrDefault(SettingService.MAIL_HOST)).thenReturn("");
        when(settingService.getOrDefault(SettingService.MAIL_FROM)).thenReturn("");
        properties.setFrom("env@example.com");

        SystemMailService service = newService("smtp.env.com", "env@example.com");
        assertThat(service.ready()).isTrue();
        assertThat(service.from()).isEqualTo("env@example.com");
    }

    @Test
    void 完全未配置时ready为假() {
        when(settingService.getOrDefault(SettingService.MAIL_HOST)).thenReturn("");
        when(settingService.getOrDefault(SettingService.MAIL_FROM)).thenReturn("");
        properties.setFrom("");

        SystemMailService service = newService("", "");
        assertThat(service.ready()).isFalse();
        assertThatThrownBy(() -> service.sendTest("a@b.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(503);
    }

    @Test
    void 测试收件人非法被拒绝() {
        when(settingService.getOrDefault(SettingService.MAIL_HOST)).thenReturn("smtp.example.com");
        when(settingService.getOrDefault(SettingService.MAIL_FROM)).thenReturn("noreply@example.com");
        when(settingService.getOrDefault(SettingService.MAIL_PORT)).thenReturn("25");
        when(settingService.getOrDefault(SettingService.MAIL_USERNAME)).thenReturn("");
        when(settingService.getOrDefault(SettingService.MAIL_SMTP_AUTH)).thenReturn("false");
        when(settingService.getOrDefault(SettingService.MAIL_SMTP_STARTTLS)).thenReturn("false");
        when(settingService.get(SettingService.MAIL_PASSWORD)).thenReturn(null);

        SystemMailService service = newService("", "");
        assertThatThrownBy(() -> service.sendTest("bad"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("测试收件人");
    }

    private SystemMailService newService(String envHost, String ignoredFrom) {
        return new SystemMailService(settingService, crypto, properties,
                envHost, 25, "", "", false, false);
    }
}
