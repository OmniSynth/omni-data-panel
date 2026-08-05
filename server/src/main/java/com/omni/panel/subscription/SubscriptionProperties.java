package com.omni.panel.subscription;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件订阅配置。
 */
@Component
@ConfigurationProperties(prefix = "omni.subscription")
public class SubscriptionProperties {
    /**
     * 订阅邮件发件人地址
     */
    private String from;
    /**
     * 前端站点基址，用于生成仪表盘访问链接与凭据邮件链接
     */
    private String frontendUrl;
    /**
     * 是否在订阅邮件中附带仪表盘 PDF
     */
    private boolean pdfEnabled = true;
    /**
     * 无头浏览器等待打印页就绪的超时（毫秒）
     */
    private long pdfTimeoutMs = 90_000L;

    /**
     * 返回订阅邮件发件人地址。
     *
     * @return 发件人地址
     */
    public String getFrom() {
        return from;
    }

    /**
     * 设置订阅邮件发件人地址。
     *
     * @param from 发件人地址
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * 返回前端站点基址。
     *
     * @return 前端 URL
     */
    public String getFrontendUrl() {
        return frontendUrl;
    }

    /**
     * 设置前端站点基址。
     *
     * @param frontendUrl 前端 URL
     */
    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    /**
     * 返回是否在订阅邮件中附带 PDF。
     *
     * @return 启用 PDF 附件时返回 {@code true}
     */
    public boolean isPdfEnabled() {
        return pdfEnabled;
    }

    /**
     * 设置是否在订阅邮件中附带 PDF。
     *
     * @param pdfEnabled 是否启用 PDF 附件
     */
    public void setPdfEnabled(boolean pdfEnabled) {
        this.pdfEnabled = pdfEnabled;
    }

    /**
     * 返回 PDF 渲染超时毫秒数。
     *
     * @return 超时毫秒数
     */
    public long getPdfTimeoutMs() {
        return pdfTimeoutMs;
    }

    /**
     * 设置 PDF 渲染超时毫秒数。
     *
     * @param pdfTimeoutMs 超时毫秒数
     */
    public void setPdfTimeoutMs(long pdfTimeoutMs) {
        this.pdfTimeoutMs = pdfTimeoutMs;
    }
}
