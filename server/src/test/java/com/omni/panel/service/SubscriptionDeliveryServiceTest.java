package com.omni.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.entity.SubscriptionEntity;
import com.omni.panel.entity.SysUser;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.SubscriptionMapper;
import com.omni.panel.mapper.UserMapper;
import com.omni.panel.subscription.SubscriptionProperties;

class SubscriptionDeliveryServiceTest {
    private final SubscriptionMapper subscriptionMapper = mock(SubscriptionMapper.class);
    private final DashboardMapper dashboardMapper = mock(DashboardMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final SystemMailService mailService = mock(SystemMailService.class);
    private final DashboardPdfService pdfService = mock(DashboardPdfService.class);
    private final SubscriptionProperties properties = new SubscriptionProperties();
    private SubscriptionDeliveryService service;

    @BeforeEach
    void setUp() {
        properties.setFrontendUrl("http://localhost:5173/");
        properties.setPdfEnabled(true);
        service = new SubscriptionDeliveryService(
                subscriptionMapper, dashboardMapper, userMapper, mailService, pdfService, properties);
    }

    @Test
    void 编码并校验收件用户() {
        SysUser user = user(3L, "alice", "爱丽丝", "alice@example.com", true);
        when(userMapper.selectById(3L)).thenReturn(user);

        assertThat(service.encodeRecipientUserIds(List.of(3L))).isEqualTo("3");
        assertThat(service.parseRecipientUserIds("3")).containsExactly(3L);
        assertThat(service.resolveEmails(List.of(3L))).containsExactly("alice@example.com");
        assertThat(service.recipientsLabel(List.of(3L))).isEqualTo("爱丽丝");
    }

    @Test
    void 兼容历史邮箱文本() {
        SysUser user = user(9L, "bob", "鲍勃", "bob@example.com", true);
        when(userMapper.findAll()).thenReturn(List.of(user));
        when(userMapper.selectById(9L)).thenReturn(user);

        assertThat(service.parseRecipientUserIds("bob@example.com")).containsExactly(9L);
    }

    @Test
    void 无邮箱用户不可作为收件人() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "no-mail", "无名", null, true));

        assertThatThrownBy(() -> service.encodeRecipientUserIds(List.of(2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置邮箱");
    }

    @Test
    void 启用PDF时发送附件() {
        stubSubscription(1L, true);
        when(mailService.ready()).thenReturn(true);
        when(userMapper.selectById(3L)).thenReturn(user(3L, "alice", "爱丽丝", "alice@example.com", true));
        when(pdfService.renderDashboardPdf(eq(8L), anyString())).thenReturn(new byte[] {1, 2, 3});

        service.send(1L);

        verify(mailService).sendWithPdfAttachment(
                eq(new String[] {"alice@example.com"}),
                eq("仪表盘订阅：销售看板"),
                anyString(),
                anyString(),
                eq(new byte[] {1, 2, 3}));
        verify(mailService, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void 关闭PDF时发送链接邮件() {
        properties.setPdfEnabled(false);
        stubSubscription(1L, true);
        when(mailService.ready()).thenReturn(true);
        when(userMapper.selectById(3L)).thenReturn(user(3L, "alice", "爱丽丝", "alice@example.com", true));

        service.send(1L);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailService).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("alice@example.com");
        assertThat(captor.getValue().getText()).contains("/dashboards/8/view");
        verify(pdfService, never()).renderDashboardPdf(anyLong(), anyString());
    }

    @Test
    void 手动发送允许已禁用订阅() {
        stubSubscription(2L, false);
        when(mailService.ready()).thenReturn(true);
        when(userMapper.selectById(3L)).thenReturn(user(3L, "alice", "爱丽丝", "alice@example.com", true));
        when(pdfService.renderDashboardPdf(eq(8L), anyString())).thenReturn(new byte[] {9});

        service.send(2L, false);

        verify(mailService).sendWithPdfAttachment(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void 定时发送拒绝已禁用订阅() {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(2L);
        subscription.setEnabled(false);
        when(subscriptionMapper.selectById(2L)).thenReturn(subscription);

        assertThatThrownBy(() -> service.send(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已禁用");
        verify(pdfService, never()).renderDashboardPdf(anyLong(), anyString());
    }

    private void stubSubscription(long id, boolean enabled) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(id);
        subscription.setEnabled(enabled);
        subscription.setDashboardId(8L);
        subscription.setRecipients("3");
        DashboardEntity dashboard = new DashboardEntity();
        dashboard.setId(8L);
        dashboard.setName("销售看板");
        when(subscriptionMapper.selectById(id)).thenReturn(subscription);
        when(dashboardMapper.selectById(8L)).thenReturn(dashboard);
    }

    private static SysUser user(long id, String username, String displayName, String email, boolean enabled) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setEnabled(enabled);
        user.setActivated(true);
        return user;
    }
}
