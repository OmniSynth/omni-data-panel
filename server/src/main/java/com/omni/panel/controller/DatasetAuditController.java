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

    public DatasetAuditController(DatasetAuditService service) {
        this.service = service;
    }

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

    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Integer>> cleanup(@RequestBody AuditCleanupRequest request) {
        return ApiResponse.ok(Map.of("deleted", service.cleanup(request)));
    }
}
