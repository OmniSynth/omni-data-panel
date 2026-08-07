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
import com.omni.panel.mapper.ExportAuditMapper;
import com.omni.panel.service.ExportAuditService;

/**
 * 管理端导出日志接口。
 */
@RestController
@RequestMapping("/api/admin/export-audits")
@PreAuthorize("hasRole('ADMIN')")
public class ExportAuditController {
    private final ExportAuditService service;

    /**
     * @param service 导出审计服务
     */
    public ExportAuditController(ExportAuditService service) {
        this.service = service;
    }

    /**
     * 分页查询导出审计。
     */
    @GetMapping
    public ApiResponse<PageResult<ExportAuditMapper.AuditRow>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.page(keyword, status, format, fromTime, toTime, page, size));
    }

    /**
     * 清理导出审计。
     */
    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Integer>> cleanup(@RequestBody AuditCleanupRequest request) {
        return ApiResponse.ok(Map.of("deleted", service.cleanup(request)));
    }
}
