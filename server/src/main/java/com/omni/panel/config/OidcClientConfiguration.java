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

    @Bean
    OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository repository) {
        return new InMemoryOAuth2AuthorizedClientService(repository);
    }

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository(OAuth2AuthorizedClientService clientService) {
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(clientService);
    }
}
