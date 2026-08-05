package com.omni.panel.query;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 查询执行与状态存储限制配置。
 *
 * @param timeoutSeconds        单次 JDBC 查询超时秒数
 * @param maxRows               单次查询最多返回行数
 * @param perUserConcurrency    单个用户在本实例的最大并发查询数（超额排队等待）
 * @param perSourceConcurrency  单个数据源在本实例的最大并发查询数（超额排队等待）
 * @param redisResultLimitBytes 允许写入 Redis 的查询快照最大字节数
 */
@ConfigurationProperties("omni.query")
public record QueryProperties(int timeoutSeconds, int maxRows, int perUserConcurrency,
                              int perSourceConcurrency, int redisResultLimitBytes) {
}
