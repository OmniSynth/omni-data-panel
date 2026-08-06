package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.omni.panel.config.SecurityProperties;
import com.omni.panel.service.SettingService;
import com.omni.panel.service.UserSessionRegistry;

class UserSessionRegistryTest {
    private final SettingService settingService = mock(SettingService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider =
            mock(ObjectProvider.class);
    private UserSessionRegistry registry;

    @BeforeEach
    void setUp() {
        when(redisProvider.getIfAvailable()).thenReturn(null);
        when(settingService.maxConcurrentSessions()).thenReturn(2);
        registry = new UserSessionRegistry(
                settingService,
                new SecurityProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", Duration.ofHours(8), null, null),
                redisProvider);
        registry.clearLocalForTest();
    }

    @Test
    void 超出上限时淘汰最旧会话() {
        Instant exp = Instant.now().plusSeconds(3600);
        registry.register(1L, "a", exp);
        registry.register(1L, "b", exp);
        registry.register(1L, "c", exp);

        assertThat(registry.localJtisForTest(1L)).containsExactly("b", "c");
        assertThat(registry.isActive(1L, "a")).isFalse();
        assertThat(registry.isActive(1L, "b")).isTrue();
        assertThat(registry.isActive(1L, "c")).isTrue();
    }

    @Test
    void 零表示不限制() {
        when(settingService.maxConcurrentSessions()).thenReturn(0);
        Instant exp = Instant.now().plusSeconds(3600);
        registry.register(1L, "a", exp);
        assertThat(registry.isActive(1L, "missing")).isTrue();
    }
}
