package com.omni.panel.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 仅在 OIDC 启用时注册 OAuth2 客户端（从 Issuer 发现元数据）。
 */
@Configuration
@ConditionalOnProperty(prefix = "omni.security.oidc", name = "enabled", havingValue = "true")
public class OidcClientConfiguration {
    /**
     * 注册 OAuth2 客户端：从 Issuer 发现元数据并构建内存中的 ClientRegistration。
     *
     * @param properties OIDC 配置
     * @return 客户端注册仓库
     * @throws IllegalStateException OIDC 已启用但关键配置缺失时
     */
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(OidcProperties properties) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "OIDC 已启用但缺少 issuer-uri / client-id / client-secret，请检查 omni.security.oidc.*");
        }
        ClientRegistration registration = ClientRegistrations
                .fromIssuerLocation(properties.issuerUri().trim())
                .registrationId(OidcProperties.REGISTRATION_ID)
                .clientId(properties.clientId().trim())
                .clientSecret(properties.clientSecret().trim())
                .scope("openid", "profile", "email")
                .clientName(properties.clientName())
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    /**
     * 提供进程内 OAuth2 已授权客户端存储服务。
     *
     * @param repository 客户端注册仓库
     * @return 已授权客户端服务
     */
    @Bean
    OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository repository) {
        return new InMemoryOAuth2AuthorizedClientService(repository);
    }

    /**
     * 将已授权客户端与当前认证主体绑定的仓库实现。
     *
     * @param clientService 已授权客户端服务
     * @return 已授权客户端仓库
     */
    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository(OAuth2AuthorizedClientService clientService) {
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(clientService);
    }
}
