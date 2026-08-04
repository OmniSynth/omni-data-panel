package com.omni.panel.metric;

import com.omni.panel.common.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供指标 CRUD 接口。
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricController {
    private final MetricService service;

    public MetricController(MetricService service) {
        this.service = service;
    }

    /**
     * 查询指标列表。
     *
     * @param collectionId 可选集合过滤
     * @return 指标列表
     */
    @GetMapping
    public ApiResponse<List<MetricEntity>> list(@RequestParam(required = false) Long collectionId) {
        return ApiResponse.ok(service.list(collectionId));
    }

    /**
     * 查询指标详情。
     *
     * @param id 指标标识
     * @return 指标
     */
    @GetMapping("/{id}")
    public ApiResponse<MetricEntity> get(@PathVariable long id) {
        return ApiResponse.ok(service.require(id, "READ"));
    }

    /**
     * 创建指标。
     *
     * @param request 保存参数
     * @return 已创建指标
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('dataset:create')")
    public ApiResponse<MetricEntity> create(@Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.create(request.name(), request.description(), request.modelId(),
            request.expressionJson(), request.aggregation(), request.collectionId()));
    }

    /**
     * 更新指标。
     *
     * @param id 指标标识
     * @param request 保存参数
     * @return 已更新指标
     */
    @PutMapping("/{id}")
    public ApiResponse<MetricEntity> update(@PathVariable long id, @Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.update(id, request.name(), request.description(), request.modelId(),
            request.expressionJson(), request.aggregation(), request.collectionId()));
    }

    /**
     * 软删除指标。
     *
     * @param id 指标标识
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.softDelete(id);
        return ApiResponse.ok();
    }

    /**
     * 指标保存请求。
     */
    public record SaveRequest(@NotBlank String name, String description, @NotNull Long modelId,
                              @NotBlank String expressionJson, @NotBlank String aggregation,
                              Long collectionId) {}
}
