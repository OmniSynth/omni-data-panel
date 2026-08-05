package com.omni.panel.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.entity.SubscriptionEntity;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.SubscriptionMapper;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.subscription.SubscriptionProperties;

/**
 * 校验订阅并通过系统邮箱向选定用户发送仪表盘 PDF（或访问链接）。
 */
@Service
public class SubscriptionDeliveryService {
    private final SubscriptionMapper subscriptionMapper;
    private final DashboardMapper dashboardMapper;
    private final UserMapper userMapper;
    private final SystemMailService mailService;
    private final DashboardPdfService pdfService;
    private final SubscriptionProperties properties;

    /**
     * 注入订阅投递所需依赖。
     *
     * @param subscriptionMapper 订阅持久化
     * @param dashboardMapper    仪表盘持久化
     * @param userMapper         用户持久化
     * @param mailService        系统发信
     * @param pdfService         仪表盘 PDF 渲染
     * @param properties         订阅配置
     */
    public SubscriptionDeliveryService(SubscriptionMapper subscriptionMapper, DashboardMapper dashboardMapper,
                                       UserMapper userMapper, SystemMailService mailService,
                                       DashboardPdfService pdfService, SubscriptionProperties properties) {
        this.subscriptionMapper = subscriptionMapper;
        this.dashboardMapper = dashboardMapper;
        this.userMapper = userMapper;
        this.mailService = mailService;
        this.pdfService = pdfService;
        this.properties = properties;
    }

    /**
     * 向订阅配置的全部收件用户发送仪表盘邮件（定时任务路径，要求订阅已启用）。
     *
     * @param subscriptionId 订阅标识
     */
    public void send(long subscriptionId) {
        send(subscriptionId, true);
    }

    /**
     * 向订阅配置的全部收件用户发送仪表盘邮件。
     *
     * @param subscriptionId 订阅标识
     * @param requireEnabled 为 true 时禁用订阅不可发送
     */
    public void send(long subscriptionId, boolean requireEnabled) {
        SubscriptionEntity subscription = subscriptionMapper.selectById(subscriptionId);
        if (subscription == null) {
            throw new BusinessException(404, "订阅不存在");
        }
        if (requireEnabled && !Boolean.TRUE.equals(subscription.getEnabled())) {
            throw new BusinessException("订阅不存在或已禁用");
        }
        DashboardEntity dashboard = dashboardMapper.selectById(subscription.getDashboardId());
        if (dashboard == null) {
            throw new BusinessException(404, "仪表盘不存在");
        }
        if (!mailService.ready()) {
            throw new BusinessException(503, "邮件服务未配置");
        }
        List<String> emails = resolveEmails(parseRecipientUserIds(subscription.getRecipients()));
        String baseUrl = properties.getFrontendUrl() == null ? "" : properties.getFrontendUrl().replaceAll("/+$", "");
        String viewUrl = baseUrl + "/dashboards/" + dashboard.getId() + "/view";
        String subject = "仪表盘订阅：" + dashboard.getName();
        String text = "仪表盘：" + dashboard.getName()
                + "\n请查收附件中的 PDF 报告。"
                + "\n也可在线查看：" + viewUrl;

        if (properties.isPdfEnabled()) {
            byte[] pdf = pdfService.renderDashboardPdf(dashboard.getId(), dashboard.getName());
            String filename = DashboardPdfService.sanitizeFilename(dashboard.getName());
            mailService.sendWithPdfAttachment(emails.toArray(String[]::new), subject, text, filename, pdf);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(emails.toArray(String[]::new));
        message.setSubject(subject);
        message.setText("仪表盘：" + dashboard.getName() + "\n访问链接：" + viewUrl);
        mailService.send(message);
    }

    /**
     * 校验收件用户并编码为持久化文本（逗号分隔的用户标识）。
     *
     * @param recipientUserIds 收件用户标识
     * @return 持久化用收件人文本
     */
    public String encodeRecipientUserIds(List<Long> recipientUserIds) {
        List<Long> normalized = normalizeRecipientUserIds(recipientUserIds);
        requireMailableUsers(normalized);
        return normalized.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    /**
     * 解析持久化收件人：优先按用户标识；兼容历史自由文本邮箱（按邮箱反查用户）。
     *
     * @param recipients 收件人持久化文本
     * @return 去重后的用户标识
     */
    public List<Long> parseRecipientUserIds(String recipients) {
        if (recipients == null || recipients.isBlank()) {
            throw new BusinessException("订阅收件人不能为空");
        }
        List<String> tokens = Arrays.stream(recipients.split("[,;\\r\\n]+"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (tokens.isEmpty()) {
            throw new BusinessException("订阅收件人不能为空");
        }
        boolean allIds = tokens.stream().allMatch(this::isUserIdToken);
        if (allIds) {
            return normalizeRecipientUserIds(tokens.stream().map(Long::valueOf).toList());
        }
        List<SysUser> allUsers = userMapper.findAll();
        Set<Long> userIds = new LinkedHashSet<>();
        for (String token : tokens) {
            String email = token.toLowerCase(Locale.ROOT);
            SysUser user = allUsers.stream()
                    .filter(item -> item.getEmail() != null && email.equals(item.getEmail().toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .orElse(null);
            if (user == null) {
                throw new BusinessException("订阅收件人未匹配到系统用户：" + token);
            }
            userIds.add(user.getId());
        }
        return normalizeRecipientUserIds(new ArrayList<>(userIds));
    }

    /**
     * 将用户标识解析为可投递邮箱地址。
     *
     * @param recipientUserIds 收件用户标识
     * @return 邮箱列表
     */
    public List<String> resolveEmails(List<Long> recipientUserIds) {
        return requireMailableUsers(normalizeRecipientUserIds(recipientUserIds)).stream()
                .map(SysUser::getEmail)
                .map(String::trim)
                .toList();
    }

    /**
     * 生成收件人展示文案（显示名优先，否则用户名）。
     *
     * @param recipientUserIds 收件用户标识
     * @return 展示文案
     */
    public String recipientsLabel(List<Long> recipientUserIds) {
        List<Long> normalized = normalizeRecipientUserIds(recipientUserIds);
        List<String> labels = new ArrayList<>();
        for (Long userId : normalized) {
            SysUser user = userMapper.selectById(userId);
            if (user == null) {
                labels.add("用户" + userId);
                continue;
            }
            if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
                labels.add(user.getDisplayName().trim());
            } else {
                labels.add(user.getUsername());
            }
        }
        return String.join("、", labels);
    }

    /**
     * 规范化并去重收件用户标识列表。
     *
     * @param recipientUserIds 原始用户标识
     * @return 去重后的用户标识
     * @throws BusinessException 列表为空或含非法标识时
     */
    private List<Long> normalizeRecipientUserIds(List<Long> recipientUserIds) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            throw new BusinessException("订阅收件人不能为空");
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long userId : recipientUserIds) {
            if (userId == null || userId <= 0) {
                throw new BusinessException("订阅收件人不合法");
            }
            unique.add(userId);
        }
        if (unique.isEmpty()) {
            throw new BusinessException("订阅收件人不能为空");
        }
        return List.copyOf(unique);
    }

    /**
     * 校验用户存在、已启用且配置了邮箱。
     *
     * @param recipientUserIds 收件用户标识
     * @return 可投递的用户实体列表
     * @throws BusinessException 用户不可用或未配置邮箱时
     */
    private List<SysUser> requireMailableUsers(List<Long> recipientUserIds) {
        List<SysUser> users = new ArrayList<>();
        for (Long userId : recipientUserIds) {
            SysUser user = userMapper.selectById(userId);
            if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                throw new BusinessException("收件用户不存在或已禁用：" + userId);
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                throw new BusinessException("收件用户未配置邮箱："
                        + Objects.requireNonNullElse(user.getDisplayName(), user.getUsername()));
            }
            users.add(user);
        }
        return users;
    }

    /**
     * 判断持久化 token 是否为纯数字用户标识。
     *
     * @param token 收件人 token
     * @return 全部为数字时返回 {@code true}
     */
    private boolean isUserIdToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
