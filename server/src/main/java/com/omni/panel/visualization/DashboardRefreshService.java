package com.omni.panel.visualization;

import com.omni.panel.common.BusinessException;
import org.springframework.stereotype.Service;

/**
 * 复用安全渲染执行链刷新仪表盘，并记录成功刷新时间。
 */
@Service
public class DashboardRefreshService {
    private final DashboardMapper dashboardMapper;
    private final DashboardRenderService renderService;

    /**
     * 创建仪表盘刷新服务。
     *
     * @param dashboardMapper 仪表盘持久化接口
     * @param renderService 共享的安全渲染执行服务
     */
    public DashboardRefreshService(DashboardMapper dashboardMapper, DashboardRenderService renderService) {
        this.dashboardMapper = dashboardMapper;
        this.renderService = renderService;
    }

    /**
     * 刷新指定仪表盘。
     * <p>
     * 后台刷新与交互式渲染共享图表所有者身份切换和总超时逻辑；任一卡片失败时不更新刷新时间。
     *
     * @param dashboardId 仪表盘标识
     */
    public void refresh(long dashboardId) {
        DashboardEntity dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null) {
            throw new BusinessException(404, "仪表盘不存在");
        }
        var cards = renderService.executeCards(dashboardId);
        if (cards.stream().anyMatch(card -> card.error() != null)) {
            throw new BusinessException("仪表盘刷新存在失败卡片");
        }
        dashboardMapper.markRefreshed(dashboardId);
    }
}
