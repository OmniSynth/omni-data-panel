package com.omni.panel.controller;

import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.query.QueryParameterApplier;
import com.omni.panel.service.DashboardRenderService;
import com.omni.panel.service.EmbedTokenService;
import com.omni.panel.service.PermissionService;

/**
 * 提供嵌入令牌签发与只读嵌入渲染接口。
 */
@RestController
@RequestMapping("/api/embed")
public class EmbedController {
    private final EmbedTokenService embedTokenService;
    private final DashboardRenderService renderService;
    private final DashboardMapper dashboardMapper;
    private final ChartMapper chartMapper;
    private final PermissionService permissionService;
    private final QueryParameterApplier parameterApplier;

    public EmbedController(EmbedTokenService embedTokenService, DashboardRenderService renderService,
                           DashboardMapper dashboardMapper, ChartMapper chartMapper,
                           PermissionService permissionService, QueryParameterApplier parameterApplier) {
        this.embedTokenService = embedTokenService;
        this.renderService = renderService;
        this.dashboardMapper = dashboardMapper;
        this.chartMapper = chartMapper;
        this.permissionService = permissionService;
        this.parameterApplier = parameterApplier;
    }

    /**
     * 为具备写权限的资源签发短期嵌入令牌。
     *
     * @param request 签发参数（仪表盘可附带锁定参数）
     * @return 嵌入令牌
     */
    @PostMapping("/tokens")
    public ApiResponse<TokenResponse> createToken(@Valid @RequestBody CreateTokenRequest request) {
        requireWritable(request.resourceType(), request.resourceId());
        Map<String, Object> locked = resolveLockedParameters(request);
        String token = embedTokenService.create(request.resourceType(), request.resourceId(), locked);
        return ApiResponse.ok(new TokenResponse(token));
    }

    /**
     * 通过嵌入令牌渲染仪表盘（应用 JWT 内锁定参数）。
     *
     * @param token 嵌入令牌
     * @return 脱敏渲染结果
     */
    @GetMapping("/dashboards/{token}")
    public ApiResponse<DashboardRenderService.RenderedDashboard> dashboard(@PathVariable String token) {
        EmbedTokenService.EmbedClaims claims = embedTokenService.parse(token);
        if (!"DASHBOARD".equals(claims.resourceType())) {
            throw new BusinessException(404, "嵌入资源不存在");
        }
        return ApiResponse.ok(renderService.renderAsOwner(claims.resourceId(), claims.parameters()));
    }

    /**
     * 通过嵌入令牌渲染图表。
     *
     * @param token 嵌入令牌
     * @return 图表渲染结果
     */
    @GetMapping("/questions/{token}")
    public ApiResponse<DashboardRenderService.RenderedQuestion> question(@PathVariable String token) {
        EmbedTokenService.EmbedClaims claims = embedTokenService.parse(token);
        if (!"QUESTION".equals(claims.resourceType())) {
            throw new BusinessException(404, "嵌入资源不存在");
        }
        return ApiResponse.ok(renderService.renderQuestionAsOwner(claims.resourceId()));
    }

    /**
     * 解析并校验锁定参数：图表嵌入不允许携带；仪表盘须为已声明参数 id。
     *
     * @param request 签发请求
     * @return 规范化后的锁定参数
     */
    private Map<String, Object> resolveLockedParameters(CreateTokenRequest request) {
        Map<String, Object> raw = request.parameters();
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        String type = request.resourceType() == null ? "" : request.resourceType().toUpperCase();
        if ("QUESTION".equals(type)) {
            throw new BusinessException(400, "图表嵌入不支持锁定参数");
        }
        if (!"DASHBOARD".equals(type)) {
            return Map.of();
        }
        DashboardEntity dashboard = dashboardMapper.selectById(request.resourceId());
        if (dashboard == null || dashboard.getDeletedAt() != null) {
            throw new BusinessException(404, "仪表盘不存在");
        }
        Set<String> allowed = parameterApplier.parseParameterMetas(dashboard.getConfigJson()).keySet();
        return embedTokenService.requireAllowedParameters(raw, allowed);
    }

    /**
     * 校验嵌入目标资源存在且当前用户具备写权限。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     */
    private void requireWritable(String resourceType, long resourceId) {
        String type = resourceType == null ? "" : resourceType.toUpperCase();
        if ("DASHBOARD".equals(type)) {
            DashboardEntity dashboard = dashboardMapper.selectById(resourceId);
            if (dashboard == null || dashboard.getDeletedAt() != null) {
                throw new BusinessException(404, "仪表盘不存在");
            }
            permissionService.require("DASHBOARD", resourceId, dashboard.getOwnerId(), "WRITE");
            return;
        }
        if ("QUESTION".equals(type)) {
            ChartEntity chart = chartMapper.selectById(resourceId);
            if (chart == null || chart.getDeletedAt() != null) {
                throw new BusinessException(404, "图表不存在");
            }
            permissionService.require("CHART", resourceId, chart.getOwnerId(), "WRITE");
            return;
        }
        throw new BusinessException("嵌入仅支持 DASHBOARD 或 QUESTION");
    }

    /**
     * 嵌入令牌签发请求。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param parameters   可选锁定参数（仅 DASHBOARD；须为仪表盘已声明参数）
     */
    public record CreateTokenRequest(@NotBlank String resourceType, @NotNull Long resourceId,
                                     Map<String, Object> parameters) {
    }

    /**
     * 嵌入令牌响应。
     */
    public record TokenResponse(String token) {
    }
}
