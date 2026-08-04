package com.omni.panel.controller;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import com.omni.panel.entity.ScheduleEntity;
import com.omni.panel.service.ScheduleService;

/**
 * 提供通用调度任务的查询、创建、更新和删除接口。
 */
@RestController
@RequestMapping("/api/schedules")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('schedule:manage')")
public class ScheduleController {
    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    /**
     * 查询当前用户可管理的调度任务。
     *
     * @return 调度任务列表
     */
    @GetMapping
    public ApiResponse<List<ScheduleEntity>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * 创建调度任务并同步注册其 Quartz 作业。
     *
     * @param request 调度任务保存参数
     * @return 已创建的调度任务
     */
    @PostMapping
    public ApiResponse<ScheduleEntity> create(@Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.save(null, request.name(), request.scheduleType(), request.targetId(),
                request.cronExpression(), request.payloadJson(), request.enabled()));
    }

    /**
     * 更新调度任务并替换其 Quartz 作业。
     *
     * @param id      调度任务标识
     * @param request 调度任务保存参数
     * @return 已更新的调度任务
     */
    @PutMapping("/{id}")
    public ApiResponse<ScheduleEntity> update(@PathVariable long id, @Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.save(id, request.name(), request.scheduleType(), request.targetId(),
                request.cronExpression(), request.payloadJson(), request.enabled()));
    }

    /**
     * 删除调度任务及对应 Quartz 作业。
     *
     * @param id 调度任务标识
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 调度任务创建与更新请求。
     *
     * @param name           任务名称
     * @param scheduleType   调度类型
     * @param targetId       调度目标标识
     * @param cronExpression Quartz Cron 表达式
     * @param payloadJson    任务载荷 JSON
     * @param enabled        是否启用
     */
    public record SaveRequest(@NotBlank String name, @NotBlank String scheduleType,
                              @NotNull Long targetId, @NotBlank String cronExpression,
                              String payloadJson, boolean enabled) {
    }
}
