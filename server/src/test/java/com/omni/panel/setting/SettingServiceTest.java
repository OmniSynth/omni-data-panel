package com.omni.panel.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.SettingEntity;
import com.omni.panel.mapper.SettingMapper;
import com.omni.panel.service.SettingService;

class SettingServiceTest {
    private final SettingMapper mapper = mock(SettingMapper.class);
    private final SettingService service = new SettingService(mapper);

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
    void 列表在库缺失时返回默认值() {
        Map<String, String> values = service.list();
        assertThat(values).containsEntry("cache.query.enabled", "false")
                .containsEntry("cache.query.ttl-seconds", "300");
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
