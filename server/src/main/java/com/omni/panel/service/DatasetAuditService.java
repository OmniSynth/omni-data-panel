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
import com.omni.panel.entity.DatasetEntity;
import com.omni.panel.mapper.DatasetAuditMapper;

/**
 * 模型变更审计写入与管理端检索清理。
 */
@Service
public class DatasetAuditService {
    private static final Logger log = LoggerFactory.getLogger(DatasetAuditService.class);
    private static final Set<String> ACTIONS = Set.of(
            "CREATE", "UPDATE", "SOFT_DELETE", "RESTORE", "PURGE");

    private final DatasetAuditMapper mapper;

    /**
     * 注入模型审计持久化依赖。
     *
     * @param mapper 模型审计持久化
     */
    public DatasetAuditService(DatasetAuditMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 记录一次模型变更；失败不影响主业务。
     *
     * @param dataset 模型实体
     * @param action  操作类型
     * @param detail  附加说明
     */
    public void record(DatasetEntity dataset, String action, String detail) {
        if (dataset == null || action == null || action.isBlank()) {
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
            String name = dataset.getName() == null || dataset.getName().isBlank() ? "-" : dataset.getName().trim();
            if (name.length() > 200) {
                name = name.substring(0, 200);
            }
            String safeDetail = detail;
            if (safeDetail != null && safeDetail.length() > 1000) {
                safeDetail = safeDetail.substring(0, 1000);
            }
            mapper.insert(dataset.getId(), name, normalized, operatorId, safeDetail);
        } catch (RuntimeException ex) {
            log.warn("写入模型审计失败: action={}, datasetId={}", normalized, dataset.getId(), ex);
        }
    }

    /**
     * 分页检索模型变更审计（仅管理员）。
     *
     * @param keyword  关键词（模型名等）
     * @param action   操作类型过滤
     * @param fromTime 起始时间
     * @param toTime   结束时间
     * @param page     页码（从 1 起）
     * @param size     每页条数
     * @return 分页结果
     */
    public PageResult<DatasetAuditMapper.AuditRow> page(String keyword, String action,
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
        List<DatasetAuditMapper.AuditRow> items = total == 0
                ? List.of()
                : mapper.list(normalizedKeyword, normalizedAction, fromTime, toTime, offset, safeSize);
        return new PageResult<>(items, total, safePage, safeSize);
    }

    /**
     * 按条件清理模型审计记录（仅管理员）。
     *
     * @param request 清理条件
     * @return 删除条数
     */
    @Transactional
    public int cleanup(AuditCleanupRequest request) {
        requireAdmin();
        LocalDateTime before = AuditCleanupSupport.resolveBefore(request);
        return before == null ? mapper.deleteAll() : mapper.deleteBefore(before);
    }

    /** 要求当前用户为管理员。 */
    private void requireAdmin() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可管理模型日志");
        }
    }

    /**
     * 空白字符串转为 {@code null}。
     *
     * @param value 原始值
     * @return 去空白后非空则返回 trimmed，否则 {@code null}
     */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
