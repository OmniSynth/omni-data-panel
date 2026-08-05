package com.omni.panel.controller;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.service.SettingService;
import com.omni.panel.service.SystemMailService;

/**
 * 提供系统设置读写接口。认证用户可读，仅管理员可写。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingController {
    private final SettingService service;
    private final SystemMailService mailService;

    /**
     * 注入系统设置与邮件服务。
     *
     * @param service      系统设置服务
     * @param mailService  系统邮件服务
     */
    public SettingController(SettingService service, SystemMailService mailService) {
        this.service = service;
        this.mailService = mailService;
    }

    /**
     * 查询系统设置。
     *
     * @return 设置映射
     */
    @GetMapping
    public ApiResponse<Map<String, String>> list() {
        Map<String, String> values = service.list();
        values.put("mail.ready", mailService.ready() ? "true" : "false");
        return ApiResponse.ok(values);
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
        Map<String, String> updated = service.update(values);
        updated.put("mail.ready", mailService.ready() ? "true" : "false");
        return ApiResponse.ok(updated);
    }

    /**
     * 使用已保存的系统邮箱配置发送测试邮件。
     *
     * @param request 收件人
     * @return 空成功响应
     */
    @PostMapping("/mail/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> testMail(@RequestBody TestMailRequest request) {
        mailService.sendTest(request == null ? null : request.to());
        return ApiResponse.ok();
    }

    /**
     * 系统邮箱测试请求。
     *
     * @param to 收件人邮箱
     */
    public record TestMailRequest(@NotBlank String to) {
    }
}
