package com.omni.panel.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.MDC;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void 无入站头时生成并回写响应头() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        String id = response.getHeader(RequestIdFilter.HEADER);
        assertThat(id).isNotBlank().hasSizeGreaterThanOrEqualTo(8);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void 合法入站头透传() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "req-abc-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) ->
                assertThat(RequestIdFilter.current()).isEqualTo("req-abc-12345"));

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("req-abc-12345");
    }

    @Test
    void 非法入站头则重新生成() {
        assertThat(RequestIdFilter.resolve("bad id with space")).doesNotContain(" ");
        assertThat(RequestIdFilter.resolve("x")).hasSizeGreaterThanOrEqualTo(8);
    }
}
