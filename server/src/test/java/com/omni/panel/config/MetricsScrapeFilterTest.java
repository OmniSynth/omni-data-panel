package com.omni.panel.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MetricsScrapeFilterTest {
    @Test
    void 未配置令牌时返回404() throws Exception {
        MetricsScrapeFilter filter = new MetricsScrapeFilter(new ObservabilityProperties(null));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void Bearer令牌正确时放行() throws Exception {
        MetricsScrapeFilter filter = new MetricsScrapeFilter(new ObservabilityProperties("secret-token"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 自定义头令牌错误时401() throws Exception {
        MetricsScrapeFilter filter = new MetricsScrapeFilter(new ObservabilityProperties("secret-token"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader(MetricsScrapeFilter.HEADER_METRICS_TOKEN, "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void 非prometheus路径不过滤() throws Exception {
        MetricsScrapeFilter filter = new MetricsScrapeFilter(new ObservabilityProperties(null));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
