package com.omni.panel.publiclink;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omni.panel.auth.AuthenticatedUser;
import com.omni.panel.common.BusinessException;
import com.omni.panel.permission.PermissionService;
import com.omni.panel.visualization.ChartEntity;
import com.omni.panel.visualization.ChartMapper;
import com.omni.panel.visualization.DashboardEntity;
import com.omni.panel.visualization.DashboardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 管理仪表盘与问题的公开分享链接。
 */
@Service
public class PublicLinkService {
    private static final Set<String> TYPES = Set.of("DASHBOARD", "QUESTION");
    private final PublicLinkMapper mapper;
    private final DashboardMapper dashboardMapper;
    private final ChartMapper chartMapper;
    private final PermissionService permissionService;

    public PublicLinkService(PublicLinkMapper mapper, DashboardMapper dashboardMapper,
                             ChartMapper chartMapper, PermissionService permissionService) {
        this.mapper = mapper;
        this.dashboardMapper = dashboardMapper;
        this.chartMapper = chartMapper;
        this.permissionService = permissionService;
    }

    /**
     * 列出当前用户可管理的公开链接。
     *
     * @return 链接列表
     */
    public List<PublicLinkEntity> list() {
        AuthenticatedUser user = AuthenticatedUser.current();
        return mapper.selectList(Wrappers.<PublicLinkEntity>lambdaQuery().orderByDesc(PublicLinkEntity::getId))
            .stream()
            .filter(link -> user.admin() || link.getCreatedBy().equals(user.id()) || isOwner(link))
            .toList();
    }

    /**
     * 创建公开链接。
     *
     * @param resourceType 资源类型 DASHBOARD|QUESTION
     * @param resourceId 资源标识
     * @return 已创建链接
     */
    @Transactional
    public PublicLinkEntity create(String resourceType, long resourceId) {
        String type = normalize(resourceType);
        requireResourceWritable(type, resourceId);
        PublicLinkEntity link = new PublicLinkEntity();
        link.setResourceType(type);
        link.setResourceId(resourceId);
        link.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        link.setEnabled(true);
        link.setCreatedBy(AuthenticatedUser.current().id());
        link.setCreatedAt(LocalDateTime.now());
        mapper.insert(link);
        return link;
    }

    /**
     * 撤销（禁用）公开链接。
     *
     * @param id 链接标识
     */
    @Transactional
    public void revoke(long id) {
        PublicLinkEntity link = requireManageable(id);
        link.setEnabled(false);
        mapper.updateById(link);
    }

    /**
     * 按 token 获取启用中的公开链接。
     *
     * @param token 公开令牌
     * @return 链接
     */
    public PublicLinkEntity getByToken(String token) {
        PublicLinkEntity link = mapper.selectOne(Wrappers.<PublicLinkEntity>lambdaQuery()
            .eq(PublicLinkEntity::getToken, token));
        if (link == null || !Boolean.TRUE.equals(link.getEnabled())) {
            throw new BusinessException(404, "公开链接不存在或已失效");
        }
        return link;
    }

    private PublicLinkEntity requireManageable(long id) {
        PublicLinkEntity link = mapper.selectById(id);
        if (link == null) {
            throw new BusinessException(404, "公开链接不存在");
        }
        AuthenticatedUser user = AuthenticatedUser.current();
        if (!user.admin() && !link.getCreatedBy().equals(user.id()) && !isOwner(link)) {
            throw new BusinessException(403, "无权管理该公开链接");
        }
        return link;
    }

    private void requireResourceWritable(String type, long resourceId) {
        if ("DASHBOARD".equals(type)) {
            DashboardEntity dashboard = dashboardMapper.selectById(resourceId);
            if (dashboard == null || dashboard.getDeletedAt() != null) {
                throw new BusinessException(404, "仪表盘不存在");
            }
            permissionService.require("DASHBOARD", resourceId, dashboard.getOwnerId(), "WRITE");
            return;
        }
        ChartEntity chart = chartMapper.selectById(resourceId);
        if (chart == null || chart.getDeletedAt() != null) {
            throw new BusinessException(404, "问题不存在");
        }
        permissionService.require("CHART", resourceId, chart.getOwnerId(), "WRITE");
    }

    private boolean isOwner(PublicLinkEntity link) {
        if ("DASHBOARD".equals(link.getResourceType())) {
            DashboardEntity dashboard = dashboardMapper.selectById(link.getResourceId());
            return dashboard != null && dashboard.getOwnerId().equals(AuthenticatedUser.current().id());
        }
        ChartEntity chart = chartMapper.selectById(link.getResourceId());
        return chart != null && chart.getOwnerId().equals(AuthenticatedUser.current().id());
    }

    private String normalize(String resourceType) {
        String type = resourceType == null ? "" : resourceType.toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BusinessException("公开链接仅支持 DASHBOARD 或 QUESTION");
        }
        return type;
    }
}
