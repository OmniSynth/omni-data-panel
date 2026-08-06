package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * 公开 API 安全白名单契约：防止生产再出现匿名 403 空 body。
 */
class PublicApiSecurityContractTest {
    @Test
    void 安全链放行登录公开嵌入与错误分发() throws Exception {
        String security = Files.readString(
                Path.of("src/main/java/com/omni/panel/config/SecurityConfig.java"), StandardCharsets.UTF_8);
        assertThat(security).contains(
                "AntPathRequestMatcher.antMatcher(\"/api/public/**\")",
                "AntPathRequestMatcher.antMatcher(\"/api/auth/oidc/status\")",
                "AntPathRequestMatcher.antMatcher(\"/api/auth/login-challenge\")",
                "dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)",
                "authenticationEntryPoint",
                "accessDeniedHandler",
                "setEnabled(false)");
    }
}
