package com.omni.panel.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.entity.PublicLinkEntity;
import com.omni.panel.mapper.ChartMapper;
import com.omni.panel.mapper.DashboardMapper;
import com.omni.panel.mapper.PublicLinkMapper;

/**
 * 管理仪表盘与图表的公开分享链接。
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
        return mapper.selectList(Wrappers.<PublicLinkEntity>lambdaQuery()
                        .eq(PublicLinkEntity::getEnabled, true)
                        .orderByDesc(PublicLinkEntity::getId))
                .stream()
                .filter(link -> user.admin() || link.getCreatedBy().equals(user.id()) || isOwner(link))
                .toList();
    }

    /**
     * 创建公开链接。
     *
     * @param resourceType 资源类型 DASHBOARD|QUESTION
     * @param resourceId   资源标识
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
        requireManageable(id);
        mapper.update(null, Wrappers.<PublicLinkEntity>lambdaUpdate()
                .eq(PublicLinkEntity::getId, id)
                .set(PublicLinkEntity::getEnabled, false));
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

    /**
     * 加载公开链接并校验当前用户管理权限。
     *
     * @param id 链接标识
     * @return 公开链接实体
     */
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

    /**
     * 校验被分享资源存在且当前用户具备写权限。
     *
     * @param type       资源类型 DASHBOARD 或 QUESTION
     * @param resourceId 资源标识
     */
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
            throw new BusinessException(404, "图表不存在");
        }
        permissionService.require("CHART", resourceId, chart.getOwnerId(), "WRITE");
    }

    /**
     * 判断当前用户是否为链接所指向资源的所有者。
     *
     * @param link 公开链接
     * @return 是所有者时返回 {@code true}
     */
    private boolean isOwner(PublicLinkEntity link) {
        if ("DASHBOARD".equals(link.getResourceType())) {
            DashboardEntity dashboard = dashboardMapper.selectById(link.getResourceId());
            return dashboard != null && dashboard.getOwnerId().equals(AuthenticatedUser.current().id());
        }
        ChartEntity chart = chartMapper.selectById(link.getResourceId());
        return chart != null && chart.getOwnerId().equals(AuthenticatedUser.current().id());
    }

    /**
     * 规范化资源类型编码并校验合法性。
     *
     * @param resourceType 资源类型
     * @return 大写的 DASHBOARD 或 QUESTION
     */
    private String normalize(String resourceType) {
        String type = resourceType == null ? "" : resourceType.toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BusinessException("公开链接仅支持 DASHBOARD 或 QUESTION");
        }
        return type;
    }
}
