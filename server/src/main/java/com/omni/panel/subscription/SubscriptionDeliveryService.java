package com.omni.panel.subscription;

import com.omni.panel.common.BusinessException;
import com.omni.panel.visualization.DashboardEntity;
import com.omni.panel.visualization.DashboardMapper;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 校验订阅并通过 Spring Mail 发送仪表盘访问链接。
 */
@Service
public class SubscriptionDeliveryService {
    private final SubscriptionMapper subscriptionMapper;
    private final DashboardMapper dashboardMapper;
    private final JavaMailSender mailSender;
    private final SubscriptionProperties properties;
    private final String mailHost;

    public SubscriptionDeliveryService(SubscriptionMapper subscriptionMapper, DashboardMapper dashboardMapper,
                                       JavaMailSender mailSender, SubscriptionProperties properties,
                                       @Value("${spring.mail.host:}") String mailHost) {
        this.subscriptionMapper = subscriptionMapper;
        this.dashboardMapper = dashboardMapper;
        this.mailSender = mailSender;
        this.properties = properties;
        this.mailHost = mailHost;
    }

    /**
     * 向订阅配置的全部收件人发送仪表盘邮件。
     * 订阅缺失或禁用、仪表盘缺失、收件人非法时抛出业务异常；未配置
     * {@code spring.mail.host} 或 {@code MAIL_FROM} 时明确以 503 失败，不会静默跳过发送。
     * 邮件传输异常由邮件发送器向调用方传播。
     *
     * @param subscriptionId 订阅标识
     */
    public void send(long subscriptionId) {
        SubscriptionEntity subscription = subscriptionMapper.selectById(subscriptionId);
        if (subscription == null || !Boolean.TRUE.equals(subscription.getEnabled())) {
            throw new BusinessException("订阅不存在或已禁用");
        }
        DashboardEntity dashboard = dashboardMapper.selectById(subscription.getDashboardId());
        if (dashboard == null) {
            throw new BusinessException(404, "仪表盘不存在");
        }
        if (mailHost.isBlank() || properties.getFrom() == null || properties.getFrom().isBlank()) {
            throw new BusinessException(503, "邮件服务未配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(parseRecipients(subscription.getRecipients()).toArray(String[]::new));
        message.setSubject("仪表盘订阅：" + dashboard.getName());
        String baseUrl = properties.getFrontendUrl() == null ? "" : properties.getFrontendUrl().replaceAll("/+$", "");
        message.setText("仪表盘：" + dashboard.getName() + "\n访问链接："
            + baseUrl + "/dashboards/" + dashboard.getId() + "/edit");
        mailSender.send(message);
    }

    /**
     * 按逗号、分号或换行拆分并严格校验收件人邮箱地址。
     *
     * @param recipients 收件人地址文本
     * @return 去除空白后的有效邮箱地址列表
     */
    public List<String> parseRecipients(String recipients) {
        List<String> values = Arrays.stream(recipients.split("[,;\\r\\n]+"))
            .map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (values.isEmpty()) {
            throw new BusinessException("订阅收件人不能为空");
        }
        try {
            for (String value : values) {
                InternetAddress address = new InternetAddress(value);
                address.validate();
                if (!value.equals(address.getAddress())) {
                    throw new AddressException();
                }
            }
        } catch (AddressException exception) {
            throw new BusinessException("订阅收件人邮箱格式不合法");
        }
        return values;
    }
}
