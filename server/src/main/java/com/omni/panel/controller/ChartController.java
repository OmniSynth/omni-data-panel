package com.omni.panel.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.service.ChartService;
import com.omni.panel.service.RecentService;

/**
 * 提供图表查询、创建、更新和软删除接口。
 */
@RestController
@RequestMapping("/api/charts")
public class ChartController {
    private final ChartService service;
    private final RecentService recentService;

    /**
     * 注入图表与最近访问服务。
     *
     * @param service       图表服务
     * @param recentService 最近访问记录服务
     */
    public ChartController(ChartService service, RecentService recentService) {
        this.service = service;
        this.recentService = recentService;
    }

    /**
     * 查询当前用户可读的全部图表。
     *
     * @return 可读图表列表
     */
    @GetMapping
    public ApiResponse<List<ChartEntity>> list() {
        return ApiResponse.ok(service.listReadable());
    }

    /**
     * 查询指定图表。
     *
     * @param id 图表标识
     * @return 具备读取权限的图表
     */
    @GetMapping("/{id}")
    public ApiResponse<ChartEntity> get(@PathVariable long id) {
        ChartEntity chart = service.require(id, "READ");
        recentService.touch(AuthenticatedUser.current().id(), "QUESTION", id);
        return ApiResponse.ok(chart);
    }

    /**
     * 创建图表并将当前用户记录为所有者。
     *
     * @param request 图表保存参数
     * @return 已创建的图表
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('chart:create')")
    public ApiResponse<ChartEntity> create(@Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.create(toService(request)));
    }

    /**
     * 更新指定图表。
     *
     * @param id      图表标识
     * @param request 图表保存参数
     * @return 已更新的图表
     */
    @PutMapping("/{id}")
    public ApiResponse<ChartEntity> update(@PathVariable long id, @Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.update(id, toService(request)));
    }

    /**
     * 软删除具备写权限的图表。
     *
     * @param id 图表标识
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.softDelete(id);
        return ApiResponse.ok();
    }

    /**
     * 将 HTTP 保存请求映射为服务层保存参数。
     *
     * @param request 控制器入参
     * @return 服务层保存请求
     */
    private ChartService.SaveRequest toService(SaveRequest request) {
        return new ChartService.SaveRequest(request.name(), request.description(), request.datasetId(),
                request.dataSourceId(), request.queryJson(), request.chartType(), request.configJson(),
                request.collectionId());
    }

    /**
     * 图表创建与更新请求。
     *
     * @param name           名称
     * @param description    描述
     * @param datasetId      语义查询关联的模型标识
     * @param dataSourceId   原生 SQL 关联的数据源标识
     * @param queryJson      查询定义 JSON
     * @param chartType      图表类型
     * @param configJson     图表配置 JSON
     * @param collectionId   所属集合标识
     */
    public record SaveRequest(@NotBlank String name, String description, Long datasetId, Long dataSourceId,
                              @NotBlank String queryJson, @NotBlank String chartType,
                              @NotBlank String configJson, Long collectionId) {
    }
}
