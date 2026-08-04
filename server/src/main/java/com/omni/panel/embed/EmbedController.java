package com.omni.panel.embed;

import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.BusinessException;
import com.omni.panel.permission.PermissionService;
import com.omni.panel.visualization.ChartEntity;
import com.omni.panel.visualization.ChartMapper;
import com.omni.panel.visualization.DashboardEntity;
import com.omni.panel.visualization.DashboardMapper;
import com.omni.panel.visualization.DashboardRenderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public EmbedController(EmbedTokenService embedTokenService, DashboardRenderService renderService,
                           DashboardMapper dashboardMapper, ChartMapper chartMapper,
                           PermissionService permissionService) {
        this.embedTokenService = embedTokenService;
        this.renderService = renderService;
        this.dashboardMapper = dashboardMapper;
        this.chartMapper = chartMapper;
        this.permissionService = permissionService;
    }

    /**
     * 为具备写权限的资源签发短期嵌入令牌。
     *
     * @param request 签发参数
     * @return 嵌入令牌
     */
    @PostMapping("/tokens")
    public ApiResponse<TokenResponse> createToken(@Valid @RequestBody CreateTokenRequest request) {
        requireWritable(request.resourceType(), request.resourceId());
        String token = embedTokenService.create(request.resourceType(), request.resourceId());
        return ApiResponse.ok(new TokenResponse(token));
    }

    /**
     * 通过嵌入令牌渲染仪表盘。
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
        return ApiResponse.ok(renderService.renderAsOwner(claims.resourceId()));
    }

    /**
     * 通过嵌入令牌渲染问题。
     *
     * @param token 嵌入令牌
     * @return 问题渲染结果
     */
    @GetMapping("/questions/{token}")
    public ApiResponse<DashboardRenderService.RenderedQuestion> question(@PathVariable String token) {
        EmbedTokenService.EmbedClaims claims = embedTokenService.parse(token);
        if (!"QUESTION".equals(claims.resourceType())) {
            throw new BusinessException(404, "嵌入资源不存在");
        }
        return ApiResponse.ok(renderService.renderQuestionAsOwner(claims.resourceId()));
    }

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
                throw new BusinessException(404, "问题不存在");
            }
            permissionService.require("CHART", resourceId, chart.getOwnerId(), "WRITE");
            return;
        }
        throw new BusinessException("嵌入仅支持 DASHBOARD 或 QUESTION");
    }

    /**
     * 嵌入令牌签发请求。
     */
    public record CreateTokenRequest(@NotBlank String resourceType, @NotNull Long resourceId) {}

    /**
     * 嵌入令牌响应。
     */
    public record TokenResponse(String token) {}
}
