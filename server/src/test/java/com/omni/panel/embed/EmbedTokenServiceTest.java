package com.omni.panel.embed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.SecurityProperties;
import com.omni.panel.service.EmbedTokenService;
import com.omni.panel.service.SettingService;

class EmbedTokenServiceTest {
    private static final String JWT_SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private SettingService settingService;
    private EmbedTokenService service;

    @BeforeEach
    void setUp() {
        settingService = mock(SettingService.class);
        when(settingService.embedEnabled()).thenReturn(true);
        service = new EmbedTokenService(
                new SecurityProperties(JWT_SECRET, Duration.ofHours(8), null, null),
                settingService);
    }

    @Test
    void 签发并解析含锁定参数() {
        Map<String, Object> locked = Map.of("dept_id", "华东", "year", 2026);
        String token = service.create("DASHBOARD", 42L, locked);

        EmbedTokenService.EmbedClaims claims = service.parse(token);
        assertThat(claims.resourceType()).isEqualTo("DASHBOARD");
        assertThat(claims.resourceId()).isEqualTo(42L);
        assertThat(claims.parameters()).containsEntry("dept_id", "华东");
        assertThat(claims.parameters()).containsEntry("year", 2026);
    }

    @Test
    void 无锁定参数时claim为空Map() {
        String token = service.create("QUESTION", 7L);
        EmbedTokenService.EmbedClaims claims = service.parse(token);
        assertThat(claims.resourceType()).isEqualTo("QUESTION");
        assertThat(claims.resourceId()).isEqualTo(7L);
        assertThat(claims.parameters()).isEmpty();
    }

    @Test
    void 校验仅允许已声明参数() {
        Map<String, Object> ok = service.requireAllowedParameters(
                Map.of("dept_id", "华东"), Set.of("dept_id", "region"));
        assertThat(ok).containsEntry("dept_id", "华东");

        assertThatThrownBy(() -> service.requireAllowedParameters(
                        Map.of("unknown", "x"), Set.of("dept_id")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("锁定参数不存在");
    }

    @Test
    void 锁定参数超过上限拒绝() {
        Map<String, Object> tooMany = new HashMap<>();
        for (int i = 0; i < EmbedTokenService.MAX_LOCKED_PARAMETERS + 1; i++) {
            tooMany.put("p" + i, i);
        }
        assertThatThrownBy(() -> service.sanitizeParameters(tooMany))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多");
    }

    @Test
    void 嵌入关闭时拒绝签发() {
        when(settingService.embedEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.create("DASHBOARD", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("嵌入功能已关闭");
    }
}
