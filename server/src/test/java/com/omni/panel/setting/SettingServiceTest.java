package com.omni.panel.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.datasource.CredentialCrypto;
import com.omni.panel.entity.SettingEntity;
import com.omni.panel.mapper.SettingMapper;
import com.omni.panel.service.SettingService;

class SettingServiceTest {
    private final SettingMapper mapper = mock(SettingMapper.class);
    private final CredentialCrypto crypto = mock(CredentialCrypto.class);
    private final SettingService service = new SettingService(mapper, crypto);

    @AfterEach
    void 清理() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 缺省关闭查询缓存并回落TTL() {
        assertThat(service.queryCacheEnabled()).isFalse();
        assertThat(service.queryCacheTtlSeconds()).isEqualTo(300);
    }

    @Test
    void 列表在库缺失时返回默认值且不暴露密码() {
        Map<String, String> values = service.list();
        assertThat(values).containsEntry("cache.query.enabled", "false")
                .containsEntry("cache.query.ttl-seconds", "300")
                .containsEntry("embed.allowed-origins", "")
                .containsEntry("mail.port", "25")
                .containsEntry("mail.password.set", "false")
                .doesNotContainKey("mail.password");
    }

    @Test
    void 缺省frameAncestors仅允许self() {
        assertThat(service.frameAncestorsCsp()).isEqualTo("frame-ancestors 'self'");
        assertThat(service.embedAllowedOrigins()).isEmpty();
    }

    @Test
    void 管理员可保存嵌入域名白名单() {
        asAdmin();
        when(mapper.selectById("embed.allowed-origins")).thenReturn(null);

        service.update(Map.of("embed.allowed-origins",
                "https://App.Example.com\nhttps://portal.example.com:8443, http://localhost:3000"));

        ArgumentCaptor<SettingEntity> captor = ArgumentCaptor.forClass(SettingEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getSettingKey()).isEqualTo("embed.allowed-origins");
        assertThat(captor.getValue().getSettingValue())
                .isEqualTo("https://app.example.com\nhttps://portal.example.com:8443\nhttp://localhost:3000");

        SettingEntity stored = new SettingEntity();
        stored.setSettingKey("embed.allowed-origins");
        stored.setSettingValue(captor.getValue().getSettingValue());
        when(mapper.selectById("embed.allowed-origins")).thenReturn(stored);
        assertThat(service.frameAncestorsCsp())
                .isEqualTo("frame-ancestors 'self' https://app.example.com https://portal.example.com:8443 http://localhost:3000");
    }

    @Test
    void 非法嵌入域名被拒绝() {
        asAdmin();
        assertThatThrownBy(() -> service.update(Map.of("embed.allowed-origins", "https://evil.example.com/path")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路径");
        assertThatThrownBy(() -> service.update(Map.of("embed.allowed-origins", "*")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(Map.of("embed.allowed-origins", "ftp://files.example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("http/https");
        verify(mapper, never()).insert(any(SettingEntity.class));
    }

    @Test
    void 公开品牌仅返回站点名称() {
        Map<String, String> values = service.publicBranding();
        assertThat(values).containsOnlyKeys("site.name")
                .containsEntry("site.name", "全域数据分析");
    }

    @Test
    void 列表在密码已配置时仅标记已设置() {
        SettingEntity password = new SettingEntity();
        password.setSettingKey("mail.password");
        password.setSettingValue("enc");
        when(mapper.selectById("mail.password")).thenReturn(password);

        Map<String, String> values = service.list();
        assertThat(values).containsEntry("mail.password.set", "true")
                .doesNotContainKey("mail.password");
    }

    @Test
    void 管理员可更新缓存设置() {
        asAdmin();
        when(mapper.selectById("cache.query.enabled")).thenReturn(null);
        when(mapper.selectById("cache.query.ttl-seconds")).thenReturn(null);

        service.update(Map.of(
                "cache.query.enabled", "true",
                "cache.query.ttl-seconds", "600"));

        verify(mapper, times(2)).insert(any(SettingEntity.class));
    }

    @Test
    void 管理员可保存邮件配置并加密密码() {
        asAdmin();
        when(crypto.encrypt("secret")).thenReturn("cipher");
        when(mapper.selectById(anyString())).thenReturn(null);

        service.update(Map.of(
                "mail.host", "smtp.example.com",
                "mail.port", "465",
                "mail.from", "noreply@example.com",
                "mail.username", "noreply@example.com",
                "mail.password", "secret",
                "mail.smtp.auth", "true",
                "mail.smtp.starttls", "false"));

        ArgumentCaptor<SettingEntity> captor = ArgumentCaptor.forClass(SettingEntity.class);
        verify(mapper, times(7)).insert(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(entity -> {
            assertThat(entity.getSettingKey()).isEqualTo("mail.password");
            assertThat(entity.getSettingValue()).isEqualTo("cipher");
        });
        verify(crypto).encrypt("secret");
    }

    @Test
    void 空密码不覆盖已有邮件密码() {
        asAdmin();
        SettingEntity existing = new SettingEntity();
        existing.setSettingKey("mail.password");
        existing.setSettingValue("old-cipher");
        when(mapper.selectById("mail.host")).thenReturn(null);
        when(mapper.selectById("mail.password")).thenReturn(existing);

        service.update(Map.of(
                "mail.host", "smtp.example.com",
                "mail.password", ""));

        verify(crypto, never()).encrypt(anyString());
        verify(mapper, never()).updateById(existing);
        verify(mapper).insert(any(SettingEntity.class));
    }

    @Test
    void 非法邮件端口被拒绝() {
        asAdmin();
        assertThatThrownBy(() -> service.update(Map.of("mail.port", "70000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮件端口");
        verify(mapper, never()).insert(any(SettingEntity.class));
    }

    @Test
    void 非法发件人被拒绝() {
        asAdmin();
        assertThatThrownBy(() -> service.update(Map.of("mail.from", "not-an-email")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发件人");
    }

    @Test
    void 非法TTL被拒绝() {
        asAdmin();
        assertThatThrownBy(() -> service.update(Map.of("cache.query.ttl-seconds", "10")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缓存时间");
        verify(mapper, never()).insert(any(SettingEntity.class));
    }

    @Test
    void 非管理员不可更新() {
        AuthenticatedUser user = new AuthenticatedUser(2L, "u", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        assertThatThrownBy(() -> service.update(Map.of("site.name", "x")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(403);
    }

    private void asAdmin() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "admin", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
