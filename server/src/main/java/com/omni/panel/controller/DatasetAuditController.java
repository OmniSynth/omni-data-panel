package com.omni.panel.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.AuditCleanupRequest;
import com.omni.panel.common.PageResult;
import com.omni.panel.mapper.DatasetAuditMapper;
import com.omni.panel.service.DatasetAuditService;

/**
 * 管理端模型变更日志接口。
 */
@RestController
@RequestMapping("/api/admin/dataset-audits")
@PreAuthorize("hasRole('ADMIN')")
public class DatasetAuditController {
    private final DatasetAuditService service;

    /**
     * @param service 模型变更审计服务
     */
    public DatasetAuditController(DatasetAuditService service) {
        this.service = service;
    }

    /**
     * 分页查询模型变更审计。
     *
     * @param keyword  关键字
     * @param action   动作类型
     * @param fromTime 起始时间
     * @param toTime   结束时间
     * @param page     页码
     * @param size     页大小
     * @return 分页数据
     */
    @GetMapping
    public ApiResponse<PageResult<DatasetAuditMapper.AuditRow>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.page(keyword, action, fromTime, toTime, page, size));
    }

    /**
     * 清理模型变更审计。
     *
     * @param request 清理条件
     * @return 含 {@code deleted} 删除行数的映射
     */
    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Integer>> cleanup(@RequestBody AuditCleanupRequest request) {
        return ApiResponse.ok(Map.of("deleted", service.cleanup(request)));
    }
}
