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

/**
 * 提供无需登录的公开仪表盘与图表只读访问。
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final PublicLinkService publicLinkService;
    private final DashboardRenderService renderService;

    public PublicController(PublicLinkService publicLinkService, DashboardRenderService renderService) {
        this.publicLinkService = publicLinkService;
        this.renderService = renderService;
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
