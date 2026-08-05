package com.omni.panel.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.PublicLinkEntity;
import com.omni.panel.service.DashboardRenderService;
import com.omni.panel.service.PublicLinkService;
import com.omni.panel.service.SubscriptionPrintTokenService;

/**
 * 提供无需登录的公开仪表盘与图表只读访问。
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final PublicLinkService publicLinkService;
    private final DashboardRenderService renderService;
    private final SubscriptionPrintTokenService printTokenService;

    public PublicController(PublicLinkService publicLinkService, DashboardRenderService renderService,
                            SubscriptionPrintTokenService printTokenService) {
        this.publicLinkService = publicLinkService;
        this.renderService = renderService;
        this.printTokenService = printTokenService;
    }

    /**
     * 通过公开令牌渲染仪表盘。
     *
     * @param token 公开令牌
     * @return 脱敏渲染结果
     */
    @GetMapping("/dashboards/{token}")
    public ApiResponse<DashboardRenderService.RenderedDashboard> dashboard(@PathVariable String token) {
        PublicLinkEntity link = publicLinkService.getByToken(token);
        if (!"DASHBOARD".equals(link.getResourceType())) {
            throw new BusinessException(404, "公开链接不存在或已失效");
        }
        return ApiResponse.ok(renderService.renderAsOwner(link.getResourceId()));
    }

    /**
     * 通过订阅打印令牌渲染仪表盘（供无头浏览器导出 PDF）。
     *
     * @param token 打印令牌
     * @return 脱敏渲染结果
     */
    @GetMapping("/print/dashboards/{token}")
    public ApiResponse<DashboardRenderService.RenderedDashboard> printDashboard(@PathVariable String token) {
        long dashboardId = printTokenService.parseDashboardId(token);
        return ApiResponse.ok(renderService.renderAsOwner(dashboardId));
    }

    /**
     * 通过公开令牌渲染图表。
     *
     * @param token 公开令牌
     * @return 图表名称与执行结果
     */
    @GetMapping("/questions/{token}")
    public ApiResponse<DashboardRenderService.RenderedQuestion> question(@PathVariable String token) {
        PublicLinkEntity link = publicLinkService.getByToken(token);
        if (!"QUESTION".equals(link.getResourceType())) {
            throw new BusinessException(404, "公开链接不存在或已失效");
        }
        return ApiResponse.ok(renderService.renderQuestionAsOwner(link.getResourceId()));
    }
}
