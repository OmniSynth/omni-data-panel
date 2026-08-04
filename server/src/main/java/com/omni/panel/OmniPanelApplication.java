package com.omni.panel;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Omni Data Panel 服务端应用入口，启用异步任务、定时任务、MyBatis Mapper 与配置属性扫描。
 */
@EnableAsync
@EnableScheduling
@MapperScan("com.omni.panel")
@ConfigurationPropertiesScan
@SpringBootApplication
public class OmniPanelApplication {
    public static void main(String[] args) {
        SpringApplication.run(OmniPanelApplication.class, args);
    }
}
