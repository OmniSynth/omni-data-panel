package com.omni.panel.controller;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.PageResult;
import com.omni.panel.logging.SystemLogBuffer;
import com.omni.panel.service.SystemLogService;

/**
 * 管理端系统日志（内存缓冲）接口。
 */
@RestController
@RequestMapping("/api/admin/system-logs")
@PreAuthorize("hasRole('ADMIN')")
public class SystemLogController {
    private final SystemLogService service;

    public SystemLogController(SystemLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<SystemLogBuffer.Entry>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.page(keyword, level, page, size));
    }

    @GetMapping("/meta")
    public ApiResponse<SystemLogService.SystemLogMeta> meta() {
        return ApiResponse.ok(service.meta());
    }

    @PostMapping("/clear")
    public ApiResponse<Map<String, Boolean>> clear() {
        service.clear();
        return ApiResponse.ok(Map.of("cleared", true));
    }
}
