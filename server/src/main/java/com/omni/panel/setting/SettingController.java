package com.omni.panel.setting;

import com.omni.panel.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 提供系统设置读写接口。认证用户可读，仅管理员可写。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingController {
    private final SettingService service;

    public SettingController(SettingService service) {
        this.service = service;
    }

    /**
     * 查询系统设置。
     *
     * @return 设置映射
     */
    @GetMapping
    public ApiResponse<Map<String, String>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * 更新系统设置。
     *
     * @param values 设置映射
     * @return 更新后的设置
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, String>> update(@RequestBody Map<String, String> values) {
        return ApiResponse.ok(service.update(values));
    }
}
