package com.omni.panel.query;

import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.AuditCleanupRequest;
import com.omni.panel.common.PageResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理端查询审计接口。
 */
@RestController
@RequestMapping("/api/admin/query-audits")
@PreAuthorize("hasRole('ADMIN')")
public class QueryAuditController {
    private final QueryAuditService service;

    public QueryAuditController(QueryAuditService service) {
        this.service = service;
    }

    /**
     * 分页检索查询审计。
     */
    @GetMapping
    public ApiResponse<PageResult<QueryAuditMapper.AuditRow>> page(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) Long sourceId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.page(keyword, status, userId, sourceId, fromTime, toTime, page, size));
    }

    /**
     * 审计详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<QueryAuditMapper.AuditRow> detail(@PathVariable long id) {
        return ApiResponse.ok(service.require(id));
    }

    /**
     * 清理查询审计日志。
     */
    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Integer>> cleanup(@RequestBody AuditCleanupRequest request) {
        return ApiResponse.ok(Map.of("deleted", service.cleanup(request)));
    }
}
