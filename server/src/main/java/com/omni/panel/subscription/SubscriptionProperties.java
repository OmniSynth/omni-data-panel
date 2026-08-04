package com.omni.panel.subscription;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件订阅配置。
 * <p>
 * {@code from} 由环境变量 {@code MAIL_FROM} 提供，{@code frontendUrl} 由
 * {@code FRONTEND_URL} 提供并默认指向本地前端。只有同时配置发件人和
 * {@code spring.mail.host} 时邮件发送能力才可用。
 */
@Component
@ConfigurationProperties(prefix = "omni.subscription")
public class SubscriptionProperties {
    private String from;
    private String frontendUrl;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }
}
