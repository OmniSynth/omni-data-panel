package com.omni.panel.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.omni.panel.common.BusinessException;
import com.omni.panel.subscription.SubscriptionProperties;

/**
 * 通过无头 Chromium 打开订阅打印页并导出仪表盘 PDF。
 */
@Service
public class DashboardPdfService {
    private final SubscriptionPrintTokenService printTokenService;
    private final SubscriptionProperties properties;
    private final AtomicReference<Playwright> playwrightRef = new AtomicReference<>();
    private final AtomicReference<Browser> browserRef = new AtomicReference<>();

    /**
     * 注入 PDF 渲染所需依赖。
     *
     * @param printTokenService 订阅打印令牌服务
     * @param properties        订阅配置
     */
    public DashboardPdfService(SubscriptionPrintTokenService printTokenService,
                               SubscriptionProperties properties) {
        this.printTokenService = printTokenService;
        this.properties = properties;
    }

    /**
     * 渲染指定仪表盘为 PDF 字节。
     *
     * @param dashboardId   仪表盘标识
     * @param dashboardName 用于异常信息的名称
     * @return PDF 内容
     */
    public byte[] renderDashboardPdf(long dashboardId, String dashboardName) {
        String baseUrl = properties.getFrontendUrl() == null ? "" : properties.getFrontendUrl().replaceAll("/+$", "");
        if (baseUrl.isBlank()) {
            throw new BusinessException(503, "未配置 FRONTEND_URL，无法生成订阅 PDF");
        }
        String token = printTokenService.create(dashboardId);
        String url = baseUrl + "/print/dashboard/" + token;
        long timeout = Math.max(10_000L, properties.getPdfTimeoutMs());
        try {
            Browser browser = browser();
            try (var context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1440, 900)
                    .setDeviceScaleFactor(1.0));
                 Page page = context.newPage()) {
                page.setDefaultTimeout(timeout);
                page.navigate(url, new Page.NavigateOptions().setTimeout(timeout));
                page.waitForSelector("html[data-print-ready='true']",
                        new Page.WaitForSelectorOptions()
                                .setState(WaitForSelectorState.ATTACHED)
                                .setTimeout(timeout));
                page.waitForTimeout(800);
                return page.pdf(new Page.PdfOptions()
                        .setPrintBackground(true)
                        .setLandscape(true)
                        .setPreferCSSPageSize(false)
                        .setFormat("A4")
                        .setMargin(new Margin()
                                .setTop("12mm")
                                .setRight("10mm")
                                .setBottom("12mm")
                                .setLeft("10mm")));
            }
        } catch (PlaywrightException exception) {
            throw new BusinessException(502, "生成仪表盘 PDF 失败（"
                    + (dashboardName == null ? String.valueOf(dashboardId) : dashboardName) + "）："
                    + rootMessage(exception)
                    + "。请确认已安装 Chromium（playwright install chromium），且 FRONTEND_URL 可被后端访问。");
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(502, "生成仪表盘 PDF 失败：" + rootMessage(exception));
        }
    }

    /**
     * 获取或懒启动无头 Chromium 浏览器实例。
     *
     * @return 已连接的浏览器
     * @throws BusinessException Chromium 无法启动时
     */
    private Browser browser() {
        Browser existing = browserRef.get();
        if (existing != null && existing.isConnected()) {
            return existing;
        }
        synchronized (this) {
            existing = browserRef.get();
            if (existing != null && existing.isConnected()) {
                return existing;
            }
            closeQuietly();
            try {
                Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(List.of("--font-render-hinting=none", "--disable-dev-shm-usage")));
                playwrightRef.set(playwright);
                browserRef.set(browser);
                return browser;
            } catch (PlaywrightException exception) {
                closeQuietly();
                throw new BusinessException(503, "无法启动 Chromium 生成 PDF：" + rootMessage(exception)
                        + "。请在 server 目录执行：mvnw exec:java -e \"-Dexec.mainClass=com.microsoft.playwright.CLI\" \"-Dexec.args=install chromium\"");
            }
        }
    }

    /** 容器销毁时关闭 Playwright 与浏览器资源。 */
    @PreDestroy
    public void destroy() {
        closeQuietly();
    }

    /** 静默关闭浏览器与 Playwright，忽略关闭异常。 */
    private void closeQuietly() {
        Browser browser = browserRef.getAndSet(null);
        if (browser != null) {
            try {
                browser.close();
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
        Playwright playwright = playwrightRef.getAndSet(null);
        if (playwright != null) {
            try {
                playwright.close();
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
    }

    /**
     * 提取异常链最内层可读消息。
     *
     * @param exception 原始异常
     * @return 根因消息或异常类名
     */
    private static String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /**
     * 清洗附件文件名，去除非法字符并确保以 .pdf 结尾。
     *
     * @param name 原始名称
     * @return 安全的 PDF 文件名
     */
    public static String sanitizeFilename(String name) {
        String cleaned = (name == null ? "" : name).trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", "_");
        if (cleaned.isBlank()) {
            cleaned = "dashboard";
        }
        if (!cleaned.toLowerCase().endsWith(".pdf")) {
            cleaned = cleaned + ".pdf";
        }
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(0, 116) + ".pdf";
        }
        return cleaned;
    }
}
