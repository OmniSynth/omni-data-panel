package com.omni.panel.subscription;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件订阅配置。
 */
@Component
@ConfigurationProperties(prefix = "omni.subscription")
public class SubscriptionProperties {
    private String from;
    private String frontendUrl;
    /** 是否在订阅邮件中附带仪表盘 PDF */
    private boolean pdfEnabled = true;
    /** 无头浏览器等待打印页就绪的超时（毫秒） */
    private long pdfTimeoutMs = 90_000L;

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

    public boolean isPdfEnabled() {
        return pdfEnabled;
    }

    public void setPdfEnabled(boolean pdfEnabled) {
        this.pdfEnabled = pdfEnabled;
    }

    public long getPdfTimeoutMs() {
        return pdfTimeoutMs;
    }

    public void setPdfTimeoutMs(long pdfTimeoutMs) {
        this.pdfTimeoutMs = pdfTimeoutMs;
    }
}
