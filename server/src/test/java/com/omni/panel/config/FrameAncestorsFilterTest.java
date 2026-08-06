package com.omni.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.omni.panel.datasource.CredentialCrypto;
import com.omni.panel.mapper.SettingMapper;
import com.omni.panel.entity.SettingEntity;
import com.omni.panel.service.SettingService;

class FrameAncestorsFilterTest {
    @Test
    void 缺省仅允许self() throws Exception {
        SettingService settings = new SettingService(mock(SettingMapper.class), mock(CredentialCrypto.class));
        FrameAncestorsFilter filter = new FrameAncestorsFilter(settings);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader(FrameAncestorsFilter.HEADER)).isEqualTo("frame-ancestors 'self'");
    }

    @Test
    void 白名单附加到frameAncestors() throws Exception {
        SettingMapper mapper = mock(SettingMapper.class);
        SettingEntity entity = new SettingEntity();
        entity.setSettingKey("embed.allowed-origins");
        entity.setSettingValue("https://app.example.com");
        when(mapper.selectById("embed.allowed-origins")).thenReturn(entity);
        FrameAncestorsFilter filter = new FrameAncestorsFilter(new SettingService(mapper, mock(CredentialCrypto.class)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader(FrameAncestorsFilter.HEADER))
                .isEqualTo("frame-ancestors 'self' https://app.example.com");
    }
}
