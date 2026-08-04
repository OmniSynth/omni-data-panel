package com.omni.panel.visualization;

import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.recent.RecentService;
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

import java.util.List;

/**
 * 提供问题（图表）查询、创建、更新和软删除接口。
 */
@RestController
@RequestMapping("/api/charts")
public class ChartController {
    private final ChartService service;
    private final RecentService recentService;

    public ChartController(ChartService service, RecentService recentService) {
        this.service = service;
        this.recentService = recentService;
    }

    /**
     * 查询当前用户可读的全部问题。
     *
     * @return 可读问题列表
     */
    @GetMapping
    public ApiResponse<List<ChartEntity>> list() {
        return ApiResponse.ok(service.listReadable());
    }

    /**
     * 查询指定问题。
     *
     * @param id 问题标识
     * @return 具备读取权限的问题
     */
    @GetMapping("/{id}")
    public ApiResponse<ChartEntity> get(@PathVariable long id) {
        ChartEntity chart = service.require(id, "READ");
        recentService.touch(AuthenticatedUser.current().id(), "QUESTION", id);
        return ApiResponse.ok(chart);
    }

    /**
     * 创建问题并将当前用户记录为所有者。
     *
     * @param request 问题保存参数
     * @return 已创建的问题
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('chart:create')")
    public ApiResponse<ChartEntity> create(@Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.create(toService(request)));
    }

    /**
     * 更新指定问题。
     *
     * @param id 问题标识
     * @param request 问题保存参数
     * @return 已更新的问题
     */
    @PutMapping("/{id}")
    public ApiResponse<ChartEntity> update(@PathVariable long id, @Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.update(id, toService(request)));
    }

    /**
     * 软删除具备写权限的问题。
     *
     * @param id 问题标识
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.softDelete(id);
        return ApiResponse.ok();
    }

    private ChartService.SaveRequest toService(SaveRequest request) {
        return new ChartService.SaveRequest(request.name(), request.description(), request.datasetId(),
            request.dataSourceId(), request.queryJson(), request.chartType(), request.configJson(),
            request.collectionId());
    }

    /**
     * 问题创建与更新请求。
     */
    public record SaveRequest(@NotBlank String name, String description, Long datasetId, Long dataSourceId,
                              @NotBlank String queryJson, @NotBlank String chartType,
                              @NotBlank String configJson, Long collectionId) {}
}
