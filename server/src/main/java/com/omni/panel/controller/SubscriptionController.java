package com.omni.panel.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.service.SubscriptionService;

/**
 * 提供仪表盘邮件订阅的查询、创建、更新和删除接口。
 */
@RestController
@RequestMapping("/api/subscriptions")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('schedule:manage')")
public class SubscriptionController {
    private final SubscriptionService service;

    /**
     * 注入订阅业务服务。
     *
     * @param service 订阅业务服务
     */
    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    /**
     * 查询当前用户可管理的订阅。
     *
     * @return 订阅列表
     */
    @GetMapping
    public ApiResponse<List<SubscriptionService.SubscriptionView>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * 创建订阅并同步注册其 Quartz 作业。
     *
     * @param request 订阅保存参数
     * @return 已创建的订阅
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('schedule:manage')")
    public ApiResponse<SubscriptionService.SubscriptionView> create(@Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.save(null, request.name(), request.dashboardId(), request.cronExpression(),
                request.recipientUserIds(), request.enabled()));
    }

    /**
     * 更新订阅并替换其 Quartz 作业。
     *
     * @param id      订阅标识
     * @param request 订阅保存参数
     * @return 已更新的订阅
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('schedule:manage')")
    public ApiResponse<SubscriptionService.SubscriptionView> update(@PathVariable long id,
                                                                    @Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.save(id, request.name(), request.dashboardId(), request.cronExpression(),
                request.recipientUserIds(), request.enabled()));
    }

    /**
     * 删除订阅及对应 Quartz 作业。
     *
     * @param id 订阅标识
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('schedule:manage')")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 立即执行一次订阅邮件发送。
     *
     * @param id 订阅标识
     * @return 空成功响应
     */
    @PostMapping("/{id}/run")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('schedule:manage')")
    public ApiResponse<Void> runNow(@PathVariable long id) {
        service.runNow(id);
        return ApiResponse.ok();
    }

    /**
     * 订阅创建与更新请求。
     *
     * @param name             订阅名称
     * @param dashboardId      仪表盘标识
     * @param cronExpression   Quartz Cron 表达式
     * @param recipientUserIds 收件用户标识列表
     * @param enabled          是否启用
     */
    public record SaveRequest(@NotBlank String name, @NotNull Long dashboardId,
                              @NotBlank String cronExpression,
                              @NotEmpty List<Long> recipientUserIds,
                              @NotNull Boolean enabled) {
    }
}
