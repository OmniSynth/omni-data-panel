package com.omni.panel.schedule;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * 配置 Quartz 作业实例的 Spring 依赖注入，使调度器创建的 Job 可以使用容器 Bean。
 */
@Configuration
public class QuartzConfig {
    @Bean
    SchedulerFactoryBeanCustomizer schedulerCustomizer(AutowireCapableBeanFactory beanFactory) {
        return scheduler -> scheduler.setJobFactory(new SpringBeanJobFactory() {
            @Override
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                beanFactory.autowireBean(job);
                return job;
            }
        });
    }
}
