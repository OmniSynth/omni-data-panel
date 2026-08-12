package com.omni.panel.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.AuditCleanupRequest;
import com.omni.panel.common.AuditCleanupSupport;
import com.omni.panel.common.BusinessException;
import com.omni.panel.common.PageResult;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.mapper.DashboardAuditMapper;

/**
 * 仪表盘变更审计写入与管理端检索清理。
 */
@Service
public class DashboardAuditService {
    private static final Logger log = LoggerFactory.getLogger(DashboardAuditService.class);
    private static final Set<String> ACTIONS = Set.of(
            "CREATE", "UPDATE", "SOFT_DELETE", "RESTORE", "PURGE",
            "CARD_CREATE", "CARD_UPDATE", "CARD_DELETE");

    private final DashboardAuditMapper mapper;
    private final SettingService settingService;

    /**
     * 注入仪表盘审计持久化与系统设置。
     *
     * @param mapper         仪表盘审计持久化
     * @param settingService 系统设置
     */
    public DashboardAuditService(DashboardAuditMapper mapper, SettingService settingService) {
        this.mapper = mapper;
        this.settingService = settingService;
    }

    /**
     * 记录一次仪表盘变更；失败不影响主业务。
     *
     * @param dashboard 仪表盘实体
     * @param action    操作类型
     * @param detail    附加说明
     */
    public void record(DashboardEntity dashboard, String action, String detail) {
        if (dashboard == null || action == null || action.isBlank()) {
            return;
        }
        String normalized = action.trim().toUpperCase();
        if (!ACTIONS.contains(normalized)) {
            return;
        }
        try {
            Long operatorId = null;
            try {
                operatorId = AuthenticatedUser.current().id();
            } catch (RuntimeException ignored) {
                // 无登录上下文时仍记录操作
            }
            String name = dashboard.getName() == null || dashboard.getName().isBlank()
                    ? "-" : dashboard.getName().trim();
            if (name.length() > 200) {
                name = name.substring(0, 200);
            }
            String safeDetail = detail;
            if (safeDetail != null && safeDetail.length() > 1000) {
                safeDetail = safeDetail.substring(0, 1000);
            }
            mapper.insert(dashboard.getId(), name, normalized, operatorId, safeDetail);
        } catch (RuntimeException ex) {
            log.warn("写入仪表盘审计失败: action={}, dashboardId={}", normalized, dashboard.getId(), ex);
        }
    }

    /**
     * 分页检索仪表盘变更审计（仅管理员）。
     *
     * @param keyword  关键词（仪表盘名等）
     * @param action   操作类型过滤
     * @param fromTime 起始时间
     * @param toTime   结束时间
     * @param page     页码（从 1 起）
     * @param size     每页条数
     * @return 分页结果
     */
    public PageResult<DashboardAuditMapper.AuditRow> page(String keyword, String action,
                                                          LocalDateTime fromTime, LocalDateTime toTime,
                                                          int page, int size) {
        requireAdmin();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedKeyword = blankToNull(keyword);
        String normalizedAction = blankToNull(action);
        if (normalizedAction != null) {
            normalizedAction = normalizedAction.toUpperCase();
        }
        long total = mapper.count(normalizedKeyword, normalizedAction, fromTime, toTime);
        List<DashboardAuditMapper.AuditRow> items = total == 0
                ? List.of()
                : mapper.list(normalizedKeyword, normalizedAction, fromTime, toTime, offset, safeSize);
        return new PageResult<>(items, total, safePage, safeSize);
    }

    /**
     * 按条件清理仪表盘审计记录（仅管理员）。
     *
     * @param request 清理条件
     * @return 删除条数
     */
    @Transactional
    public int cleanup(AuditCleanupRequest request) {
        requireAdmin();
        settingService.requireLogsClearEnabled();
        LocalDateTime before = AuditCleanupSupport.resolveBefore(request);
        return before == null ? mapper.deleteAll() : mapper.deleteBefore(before);
    }

    private void requireAdmin() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可管理仪表盘日志");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
