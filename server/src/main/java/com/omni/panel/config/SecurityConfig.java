package com.omni.panel.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import com.omni.panel.common.ApiResponse;

/**
 * 配置无状态 JWT 认证链、公开端点、安全响应头、可选 OIDC 登录与密码编码器。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    /**
     * 配置 JWT 安全过滤链、公开端点白名单、安全响应头与可选 OIDC。
     *
     * @param http                 Spring Security 配置器
     * @param jwtFilter            JWT 认证过滤器
     * @param rateLimitFilter      登录与公开接口限流过滤器
     * @param metricsScrapeFilter  Prometheus 刮取令牌过滤器
     * @param requestIdFilter      请求 ID 过滤器
     * @param frameAncestorsFilter frame-ancestors CSP 过滤器
     * @param oidcProperties       OIDC 配置
     * @param oidcSuccessHandler   OIDC 成功处理器（未启用时可为空）
     * @param oidcFailureHandler   OIDC 失败处理器（未启用时可为空）
     * @param objectMapper         JSON 序列化（鉴权失败响应）
     * @return 已构建的安全过滤链
     * @throws Exception 安全配置失败时抛出
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            RateLimitFilter rateLimitFilter,
                                            MetricsScrapeFilter metricsScrapeFilter,
                                            RequestIdFilter requestIdFilter,
                                            FrameAncestorsFilter frameAncestorsFilter,
                                            OidcProperties oidcProperties,
                                            Optional<OidcLoginSuccessHandler> oidcSuccessHandler,
                                            Optional<OidcLoginFailureHandler> oidcFailureHandler,
                                            ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint entryPoint = jsonEntryPoint(objectMapper, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已失效");
        AccessDeniedHandler deniedHandler = jsonDeniedHandler(objectMapper);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(
                        oidcProperties.isConfigured()
                                ? SessionCreationPolicy.IF_REQUIRED
                                : SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .headers(headers -> {
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.referrerPolicy(referrer -> referrer.policy(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.permissionsPolicy(permissions -> permissions.policy(
                            "camera=(), microphone=(), geolocation=()"));
                    headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable);
                })
                .authorizeHttpRequests(authorize -> authorize
                        // ERROR/FORWARD：避免业务异常转发 /error 时被二次鉴权成空 body 403
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/error")).permitAll()
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher("/api/auth/login"),
                                AntPathRequestMatcher.antMatcher("/api/auth/login-challenge"),
                                AntPathRequestMatcher.antMatcher("/api/auth/mfa/verify"),
                                AntPathRequestMatcher.antMatcher("/api/auth/setup-password"),
                                AntPathRequestMatcher.antMatcher("/api/auth/oidc/status"),
                                AntPathRequestMatcher.antMatcher("/api/auth/oidc/exchange"),
                                AntPathRequestMatcher.antMatcher("/oauth2/authorization/**"),
                                AntPathRequestMatcher.antMatcher("/login/oauth2/code/**"),
                                AntPathRequestMatcher.antMatcher("/actuator/health"),
                                AntPathRequestMatcher.antMatcher("/actuator/health/**"),
                                AntPathRequestMatcher.antMatcher("/actuator/prometheus"),
                                AntPathRequestMatcher.antMatcher("/api/public/**"),
                                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/embed/**"))
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(metricsScrapeFilter, RateLimitFilter.class)
                .addFilterBefore(requestIdFilter, MetricsScrapeFilter.class)
                .addFilterAfter(frameAncestorsFilter, RequestIdFilter.class);

        if (oidcProperties.isConfigured() && oidcSuccessHandler.isPresent()) {
            http.oauth2Login(oauth -> {
                oauth.successHandler(oidcSuccessHandler.get());
                oidcFailureHandler.ifPresent(oauth::failureHandler);
            });
        }

        return http.build();
    }

    /**
     * 禁止将已挂入 Security 链的过滤器再注册到 Servlet 容器，避免重复执行。
     *
     * @param filter 请求 ID 过滤器
     * @return 已禁用的注册器
     */
    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(RequestIdFilter filter) {
        return disabledRegistration(filter);
    }

    /**
     * @param filter frame-ancestors 过滤器
     * @return 已禁用的注册器
     */
    @Bean
    FilterRegistrationBean<FrameAncestorsFilter> frameAncestorsFilterRegistration(FrameAncestorsFilter filter) {
        return disabledRegistration(filter);
    }

    /**
     * @param filter 指标刮取过滤器
     * @return 已禁用的注册器
     */
    @Bean
    FilterRegistrationBean<MetricsScrapeFilter> metricsScrapeFilterRegistration(MetricsScrapeFilter filter) {
        return disabledRegistration(filter);
    }

    /**
     * @param filter 限流过滤器
     * @return 已禁用的注册器
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        return disabledRegistration(filter);
    }

    /**
     * @param filter JWT 认证过滤器
     * @return 已禁用的注册器
     */
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        return disabledRegistration(filter);
    }

    /**
     * 提供 BCrypt 密码编码器，用于用户凭据哈希。
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledRegistration(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private static AuthenticationEntryPoint jsonEntryPoint(ObjectMapper objectMapper, int status, String message) {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
                -> writeJsonError(objectMapper, response, status, message);
    }

    private static AccessDeniedHandler jsonDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException)
                -> writeJsonError(objectMapper, response, HttpServletResponse.SC_FORBIDDEN, "无权访问该资源");
    }

    private static void writeJsonError(ObjectMapper objectMapper, HttpServletResponse response,
                                       int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(status, message));
    }
}
