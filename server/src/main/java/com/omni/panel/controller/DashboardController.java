package com.omni.panel.controller;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
import com.omni.panel.common.ApiResponse;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.DashboardCardEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.service.DashboardRenderService;
import com.omni.panel.service.DashboardService;
import com.omni.panel.service.PermissionService;
import com.omni.panel.service.RecentService;

/**
 * 提供仪表盘及其卡片的管理接口。
 */
@RestController
@RequestMapping("/api/dashboards")
public class DashboardController {
    private final DashboardService service;
    private final PermissionService permissionService;
    private final DashboardRenderService renderService;
    private final RecentService recentService;

    public DashboardController(DashboardService service, PermissionService permissionService,
                               DashboardRenderService renderService, RecentService recentService) {
        this.service = service;
        this.permissionService = permissionService;
        this.renderService = renderService;
        this.recentService = recentService;
    }

    /**
     * 查询当前用户可读的全部仪表盘。
     *
     * @return 可读仪表盘列表
     */
    @GetMapping
    public ApiResponse<List<DashboardView>> list() {
        return ApiResponse.ok(service.listReadable().stream().map(this::view).toList());
    }

    /**
     * 查询指定仪表盘。
     *
     * @param id 仪表盘标识
     * @return 具备读取权限的仪表盘
     */
    @GetMapping("/{id}")
    public ApiResponse<DashboardView> get(@PathVariable long id) {
        DashboardEntity dashboard = service.require(id, "READ");
        recentService.touch(AuthenticatedUser.current().id(), "DASHBOARD", id);
        return ApiResponse.ok(view(dashboard));
    }

    /**
     * 查询指定仪表盘卡片。
     *
     * @param id 仪表盘标识
     * @return 卡片列表
     */
    @GetMapping("/{id}/cards")
    public ApiResponse<List<DashboardCardEntity>> cards(@PathVariable long id) {
        return ApiResponse.ok(service.cards(id));
    }

    /**
     * 安全渲染仪表盘。
     *
     * @param id           仪表盘标识
     * @param forceRefresh 为 true 时跳过查询结果缓存
     * @return 渲染结果
     */
    @GetMapping("/{id}/render")
    public ApiResponse<DashboardRenderService.RenderedDashboard> render(
            @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        return ApiResponse.ok(renderService.render(id, forceRefresh, null));
    }

    /**
     * 按参数值安全渲染仪表盘。
     *
     * @param id      仪表盘标识
     * @param request 渲染参数
     * @return 渲染结果
     */
    @PostMapping("/{id}/render")
    public ApiResponse<DashboardRenderService.RenderedDashboard> renderWithParams(
            @PathVariable long id,
            @RequestBody(required = false) RenderRequest request) {
        boolean force = request != null && Boolean.TRUE.equals(request.forceRefresh());
        var values = request == null ? null : request.parameterValues();
        return ApiResponse.ok(renderService.render(id, force, values));
    }

    /**
     * 创建仪表盘。
     *
     * @param request 保存参数
     * @return 已创建仪表盘
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('dashboard:create')")
    public ApiResponse<DashboardView> create(@Valid @RequestBody DashboardRequest request) {
        return ApiResponse.ok(view(service.create(request.name(), request.description(),
                request.configJson(), request.collectionId())));
    }

    /**
     * 更新仪表盘。
     *
     * @param id      仪表盘标识
     * @param request 保存参数
     * @return 已更新仪表盘
     */
    @PutMapping("/{id}")
    public ApiResponse<DashboardView> update(@PathVariable long id,
                                             @Valid @RequestBody DashboardRequest request) {
        return ApiResponse.ok(view(service.update(id, request.name(), request.description(),
                request.configJson(), request.collectionId())));
    }

    /**
     * 软删除仪表盘。
     *
     * @param id 仪表盘标识
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.softDelete(id);
        return ApiResponse.ok();
    }

    /**
     * 添加卡片。
     *
     * @param id      仪表盘标识
     * @param request 卡片参数
     * @return 已创建卡片
     */
    @PostMapping("/{id}/cards")
    public ApiResponse<DashboardCardEntity> createCard(@PathVariable long id,
                                                       @Valid @RequestBody CardRequest request) {
        return ApiResponse.ok(service.createCard(id, request.chartId(), request.title(), request.layoutJson(),
                request.bindingsJson(), request.clickActionJson()));
    }

    /**
     * 更新卡片。
     *
     * @param id      仪表盘标识
     * @param cardId  卡片标识
     * @param request 卡片参数
     * @return 已更新卡片
     */
    @PutMapping("/{id}/cards/{cardId}")
    public ApiResponse<DashboardCardEntity> updateCard(@PathVariable long id, @PathVariable long cardId,
                                                       @Valid @RequestBody CardRequest request) {
        return ApiResponse.ok(service.updateCard(id, cardId, request.chartId(), request.title(),
                request.layoutJson(), request.bindingsJson(), request.clickActionJson()));
    }

    /**
     * 删除卡片。
     *
     * @param id     仪表盘标识
     * @param cardId 卡片标识
     * @return 空成功响应
     */
    @DeleteMapping("/{id}/cards/{cardId}")
    public ApiResponse<Void> deleteCard(@PathVariable long id, @PathVariable long cardId) {
        service.deleteCard(id, cardId);
        return ApiResponse.ok();
    }

    /**
     * 将仪表盘实体组装为含访问级别的 API 视图。
     *
     * @param entity 仪表盘实体
     * @return 仪表盘视图
     */
    private DashboardView view(DashboardEntity entity) {
        return new DashboardView(entity.getId(), entity.getName(), entity.getDescription(),
                entity.getConfigJson(), entity.getOwnerId(), entity.getCollectionId(),
                entity.getLastRefreshedAt(), entity.getUpdatedAt(),
                permissionService.accessLevel("DASHBOARD", entity.getId()));
    }

    /**
     * 仪表盘创建与更新请求。
     */
    public record DashboardRequest(@NotBlank String name, String description,
                                   @NotBlank String configJson, Long collectionId) {
    }

    /**
     * 仪表盘卡片创建与更新请求。
     */
    public record CardRequest(@NotNull Long chartId, @NotBlank String title, @NotBlank String layoutJson,
                              String bindingsJson, String clickActionJson) {
    }

    /**
     * 带参数的仪表盘渲染请求。
     */
    public record RenderRequest(Boolean forceRefresh, java.util.Map<String, Object> parameterValues) {
    }

    /**
     * 仪表盘视图。
     */
    public record DashboardView(@JsonSerialize(using = ToStringSerializer.class) long id,
                                String name, String description, String configJson,
                                @JsonSerialize(using = ToStringSerializer.class) long ownerId,
                                @JsonSerialize(using = ToStringSerializer.class) Long collectionId,
                                LocalDateTime lastRefreshedAt, LocalDateTime updatedAt,
                                String accessLevel) {
    }
}
