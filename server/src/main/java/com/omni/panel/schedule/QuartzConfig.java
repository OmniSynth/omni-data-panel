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
    /**
     * 自定义 JobFactory：创建作业后对其执行 Spring 自动装配。
     *
     * @param beanFactory Spring 装配工厂
     * @return SchedulerFactoryBean 定制器
     */
    @Bean
    SchedulerFactoryBeanCustomizer schedulerCustomizer(AutowireCapableBeanFactory beanFactory) {
        return scheduler -> scheduler.setJobFactory(new SpringBeanJobFactory() {
            /**
             * 创建 Job 实例并注入容器依赖。
             *
             * @param bundle 触发上下文
             * @return 已装配的 Job
             */
            @Override
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                beanFactory.autowireBean(job);
                return job;
            }
        });
    }
}
