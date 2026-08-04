package com.omni.panel.subscription;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import com.omni.panel.service.SubscriptionDeliveryService;

/**
 * Quartz 订阅发送入口，确保同一订阅不会并发发送邮件。
 */
@DisallowConcurrentExecution
public class SubscriptionDispatchJob implements Job {
    @Autowired
    private SubscriptionDeliveryService deliveryService;

    /**
     * 发送执行上下文指定的订阅邮件。
     * 发送失败时包装为不立即重新触发的 Quartz 执行异常。
     *
     * @param context Quartz 执行上下文，必须包含 {@code subscriptionId}
     * @throws JobExecutionException 订阅校验或邮件发送失败
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long subscriptionId = context.getMergedJobDataMap().getLong("subscriptionId");
        try {
            deliveryService.send(subscriptionId);
        } catch (Exception exception) {
            throw new JobExecutionException("订阅邮件发送失败", exception, false);
        }
    }
}
