package com.omni.panel.datasource;

import com.omni.panel.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端数据源连接池与健康度监控接口。
 */
@RestController
@RequestMapping("/api/admin/data-source-health")
@PreAuthorize("hasRole('ADMIN')")
public class DataSourceHealthController {
    private final DataSourceHealthService service;

    public DataSourceHealthController(DataSourceHealthService service) {
        this.service = service;
    }

    /**
     * 返回全部分析数据源的连接池状态、延迟与可用性快照。
     */
    @GetMapping
    public ApiResponse<DataSourceHealthService.HealthOverview> overview() {
        return ApiResponse.ok(service.overview());
    }
}
