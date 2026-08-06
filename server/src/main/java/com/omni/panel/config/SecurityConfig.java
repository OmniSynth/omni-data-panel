package com.omni.panel.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * 配置无状态 JWT 认证链、公开端点、安全响应头、可选 OIDC 登录与密码编码器。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    /**
     * 配置 JWT 安全过滤链、公开端点白名单、安全响应头与可选 OIDC。
     *
     * @param http               Spring Security 配置器
     * @param jwtFilter          JWT 认证过滤器
     * @param rateLimitFilter    登录与公开接口限流过滤器
     * @param oidcProperties     OIDC 配置
     * @param oidcSuccessHandler OIDC 成功处理器（未启用时可为空）
     * @return 已构建的安全过滤链
     * @throws Exception 安全配置失败时抛出
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            RateLimitFilter rateLimitFilter, OidcProperties oidcProperties,
                                            Optional<OidcLoginSuccessHandler> oidcSuccessHandler,
                                            Optional<OidcLoginFailureHandler> oidcFailureHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        oidcProperties.isConfigured()
                                ? SessionCreationPolicy.IF_REQUIRED
                                : SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.referrerPolicy(referrer -> referrer.policy(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.permissionsPolicy(permissions -> permissions.policy(
                            "camera=(), microphone=(), geolocation=()"));
                    headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable);
                })
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/login-challenge",
                                "/api/auth/mfa/verify",
                                "/api/auth/setup-password",
                                "/api/auth/oidc/status",
                                "/api/auth/oidc/exchange",
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**",
                                "/actuator/health",
                                "/actuator/health/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/embed/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        if (oidcProperties.isConfigured() && oidcSuccessHandler.isPresent()) {
            http.oauth2Login(oauth -> {
                oauth.successHandler(oidcSuccessHandler.get());
                oidcFailureHandler.ifPresent(oauth::failureHandler);
            });
        }

        return http.build();
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
}
