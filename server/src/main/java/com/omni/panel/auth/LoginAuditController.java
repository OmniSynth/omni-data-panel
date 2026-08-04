package com.omni.panel.auth;

import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.AuditCleanupRequest;
import com.omni.panel.common.PageResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理端登录日志接口。
 */
@RestController
@RequestMapping("/api/admin/login-audits")
@PreAuthorize("hasRole('ADMIN')")
public class LoginAuditController {
    private final LoginAuditService service;

    public LoginAuditController(LoginAuditService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<LoginAuditMapper.LoginRow>> page(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean success,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.page(keyword, success, fromTime, toTime, page, size));
    }

    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Integer>> cleanup(@RequestBody AuditCleanupRequest request) {
        return ApiResponse.ok(Map.of("deleted", service.cleanup(request)));
    }
}
