package com.omni.panel.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.omni.panel.config.SecurityProperties;

class ClientIpResolverTest {
    @AfterEach
    void 清理() {
        ClientIpResolver holder = ClientIpResolver.current();
        if (holder != null) {
            holder.unregister();
        }
    }

    @Test
    void 无可信代理时忽略伪造XFF() {
        ClientIpResolver resolver = new ClientIpResolver(props(""));
        resolver.register();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolver.resolveClientIp(request)).isEqualTo("203.0.113.10");
        assertThat(ClientRequestInfo.from(request).clientIp()).isEqualTo("203.0.113.10");
    }

    @Test
    void 可信代理时自右向左剥链() {
        ClientIpResolver resolver = new ClientIpResolver(props("10.0.0.0/8,172.16.0.0/12"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.5");

        assertThat(resolver.resolveClientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void 可信代理多跳剥尽后回落remote() {
        ClientIpResolver resolver = new ClientIpResolver(props("10.0.0.0/8"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.1.1");
        request.addHeader("X-Forwarded-For", "10.2.2.2, 10.3.3.3");

        assertThat(resolver.resolveClientIp(request)).isEqualTo("10.1.1.1");
    }

    @Test
    void 可信代理无XFF时可用XRealIP() {
        ClientIpResolver resolver = new ClientIpResolver(props("192.168.0.0/16"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.addHeader("X-Real-IP", "198.51.100.7");

        assertThat(resolver.resolveClientIp(request)).isEqualTo("198.51.100.7");
    }

    private static SecurityProperties props(String trusted) {
        return new SecurityProperties("secret", Duration.ofHours(8), null, trusted);
    }
}
