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

    /**
     * 注入系统日志业务服务。
     *
     * @param service 系统日志服务
     */
    public SystemLogController(SystemLogService service) {
        this.service = service;
    }

    /**
     * 分页查询内存缓冲中的系统日志。
     *
     * @param keyword 可选关键字过滤
     * @param level   可选日志级别过滤
     * @param page    页码，从 1 开始
     * @param size    每页条数
     * @return 分页日志条目
     */
    @GetMapping
    public ApiResponse<PageResult<SystemLogBuffer.Entry>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.page(keyword, level, page, size));
    }

    /**
     * 查询系统日志缓冲元信息。
     *
     * @return 缓冲容量与当前条数等元数据
     */
    @GetMapping("/meta")
    public ApiResponse<SystemLogService.SystemLogMeta> meta() {
        return ApiResponse.ok(service.meta());
    }

    /**
     * 清空内存中的系统日志缓冲。
     *
     * @return 清空结果标记
     */
    @PostMapping("/clear")
    public ApiResponse<Map<String, Boolean>> clear() {
        service.clear();
        return ApiResponse.ok(Map.of("cleared", true));
    }
}
