package com.omni.panel.service;

import java.util.Properties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.CredentialCrypto;
import com.omni.panel.subscription.SubscriptionProperties;

/**
 * 系统发信服务：优先使用管理后台配置的 SMTP，未配置时回退环境变量。
 */
@Service
public class SystemMailService {
    private final SettingService settingService;
    private final CredentialCrypto crypto;
    private final SubscriptionProperties subscriptionProperties;
    private final String envHost;
    private final int envPort;
    private final String envUsername;
    private final String envPassword;
    private final boolean envAuth;
    private final boolean envStartTls;

    public SystemMailService(SettingService settingService, CredentialCrypto crypto,
                             SubscriptionProperties subscriptionProperties,
                             @Value("${spring.mail.host:}") String envHost,
                             @Value("${spring.mail.port:25}") int envPort,
                             @Value("${spring.mail.username:}") String envUsername,
                             @Value("${spring.mail.password:}") String envPassword,
                             @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean envAuth,
                             @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean envStartTls) {
        this.settingService = settingService;
        this.crypto = crypto;
        this.subscriptionProperties = subscriptionProperties;
        this.envHost = envHost == null ? "" : envHost;
        this.envPort = envPort;
        this.envUsername = envUsername == null ? "" : envUsername;
        this.envPassword = envPassword == null ? "" : envPassword;
        this.envAuth = envAuth;
        this.envStartTls = envStartTls;
    }

    /**
     * 判断系统邮件是否已具备发信条件（主机与发件人）。
     *
     * @return 可发信时返回 {@code true}
     */
    public boolean ready() {
        MailRuntime runtime = resolve();
        return runtime != null;
    }

    /**
     * 当前生效的系统发件人地址。
     *
     * @return 发件人；未配置时返回空串
     */
    public String from() {
        MailRuntime runtime = resolve();
        return runtime == null ? "" : runtime.from();
    }

    /**
     * 使用系统邮箱发送简单文本邮件；强制覆盖发件人为系统配置。
     *
     * @param message 邮件内容（需已设置收件人与主题正文）
     */
    public void send(SimpleMailMessage message) {
        MailRuntime runtime = resolve();
        if (runtime == null) {
            throw new BusinessException(503, "邮件服务未配置");
        }
        message.setFrom(runtime.from());
        try {
            runtime.sender().send(message);
        } catch (MailException exception) {
            throw new BusinessException(502, "邮件发送失败：" + rootMessage(exception));
        }
    }

    /**
     * 发送带 PDF 附件的文本邮件。
     *
     * @param to          收件人
     * @param subject     主题
     * @param text        正文
     * @param filename    附件文件名
     * @param pdfContent  PDF 字节
     */
    public void sendWithPdfAttachment(String[] to, String subject, String text,
                                      String filename, byte[] pdfContent) {
        MailRuntime runtime = resolve();
        if (runtime == null) {
            throw new BusinessException(503, "邮件服务未配置");
        }
        if (to == null || to.length == 0) {
            throw new BusinessException("收件人不能为空");
        }
        if (pdfContent == null || pdfContent.length == 0) {
            throw new BusinessException("PDF 附件不能为空");
        }
        try {
            MimeMessage mimeMessage = runtime.sender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(runtime.from());
            helper.setTo(to);
            helper.setSubject(subject == null ? "" : subject);
            helper.setText(text == null ? "" : text);
            helper.addAttachment(
                    filename == null || filename.isBlank() ? "dashboard.pdf" : filename,
                    new ByteArrayResource(pdfContent) {
                        @Override
                        public String getFilename() {
                            return filename == null || filename.isBlank() ? "dashboard.pdf" : filename;
                        }
                    },
                    "application/pdf");
            runtime.sender().send(mimeMessage);
        } catch (MessagingException | MailException exception) {
            throw new BusinessException(502, "邮件发送失败：" + rootMessage(exception));
        }
    }

    /**
     * 向指定地址发送系统邮箱连通性测试邮件。
     *
     * @param to 收件人
     */
    public void sendTest(String to) {
        String recipient = to == null ? "" : to.trim();
        if (recipient.isEmpty()) {
            throw new BusinessException("测试收件人不能为空");
        }
        try {
            InternetAddress address = new InternetAddress(recipient);
            address.validate();
            if (!recipient.equals(address.getAddress())) {
                throw new AddressException();
            }
        } catch (AddressException exception) {
            throw new BusinessException("测试收件人邮箱格式不合法");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setSubject("系统邮箱测试");
        message.setText("这是一封来自全域数据分析平台的系统邮箱测试邮件。若收到此邮件，说明 SMTP 配置可用。");
        send(message);
    }

    private MailRuntime resolve() {
        String dbHost = settingService.getOrDefault(SettingService.MAIL_HOST).trim();
        String dbFrom = settingService.getOrDefault(SettingService.MAIL_FROM).trim();
        if (!dbHost.isEmpty() && !dbFrom.isEmpty()) {
            int port = parsePort(settingService.getOrDefault(SettingService.MAIL_PORT), 25);
            String username = settingService.getOrDefault(SettingService.MAIL_USERNAME).trim();
            String password = decryptPassword(settingService.get(SettingService.MAIL_PASSWORD));
            boolean auth = Boolean.parseBoolean(settingService.getOrDefault(SettingService.MAIL_SMTP_AUTH));
            boolean startTls = Boolean.parseBoolean(settingService.getOrDefault(SettingService.MAIL_SMTP_STARTTLS));
            return new MailRuntime(buildSender(dbHost, port, username, password, auth, startTls), dbFrom);
        }
        String envFrom = subscriptionProperties.getFrom();
        if (envHost.isBlank() || envFrom == null || envFrom.isBlank()) {
            return null;
        }
        return new MailRuntime(
                buildSender(envHost.trim(), envPort, envUsername, envPassword, envAuth, envStartTls),
                envFrom.trim());
    }

    private String decryptPassword(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return "";
        }
        return crypto.decrypt(encrypted);
    }

    private static int parsePort(String raw, int fallback) {
        try {
            int port = Integer.parseInt(raw.trim());
            return port >= 1 && port <= 65_535 ? port : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static JavaMailSenderImpl buildSender(String host, int port, String username, String password,
                                                  boolean auth, boolean startTls) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            sender.setPassword(password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", Boolean.toString(auth));
        props.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    private static String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record MailRuntime(JavaMailSenderImpl sender, String from) {
    }
}
