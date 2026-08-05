package com.omni.panel.datasource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.omni.panel.entity.DataSourceEntity;
import com.omni.panel.mapper.DataSourceMapper;
import com.omni.panel.service.DataSourceService;

/**
 * 应用启动后并行预热全部可用分析数据源连接池，降低首次查询延迟。
 */
@Component
@Order(200)
public class DataSourceWarmup implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSourceWarmup.class);

    private final DataSourceMapper mapper;
    private final DataSourceRegistry registry;
    private final DataSourceService dataSourceService;

    public DataSourceWarmup(DataSourceMapper mapper, DataSourceRegistry registry,
                            DataSourceService dataSourceService) {
        this.mapper = mapper;
        this.registry = registry;
        this.dataSourceService = dataSourceService;
    }

    /**
     * 读取全部数据源并并行预热连接池；单个失败不影响应用启动与其他数据源。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        dataSourceService.backfillMissingConnectionFields();
        List<DataSourceEntity> sources = mapper.selectList(null);
        if (sources.isEmpty()) {
            log.info("无分析数据源需要预热");
            return;
        }
        log.info("开始预热 {} 个分析数据源连接池", sources.size());
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<Void>> tasks = new ArrayList<>(sources.size());
        for (DataSourceEntity source : sources) {
            tasks.add(CompletableFuture.runAsync(() -> warmOne(source), executor));
        }
        try {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
                    .orTimeout(60, TimeUnit.SECONDS)
                    .exceptionally(error -> {
                        log.warn("数据源预热超时或异常：{}", error.getMessage());
                        return null;
                    })
                    .join();
        } finally {
            executor.close();
        }
        long ready = sources.stream().filter(source -> registry.contains(source.getId())).count();
        log.info("分析数据源连接池预热完成：成功 {}/{}", ready, sources.size());
    }

    /**
     * 预热单个数据源连接池；失败仅记录日志，不中断启动流程。
     *
     * @param source 数据源配置
     */
    private void warmOne(DataSourceEntity source) {
        try {
            registry.warmUp(source);
            log.info("数据源预热成功：{} ({})", source.getName(), source.getId());
        } catch (RuntimeException exception) {
            log.warn("数据源预热失败：{} ({}) - {}", source.getName(), source.getId(), exception.getMessage());
        }
    }
}
