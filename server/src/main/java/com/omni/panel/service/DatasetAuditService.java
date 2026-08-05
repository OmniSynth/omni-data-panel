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

    public DatasetAuditService(DatasetAuditMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 记录一次模型变更；失败不影响主业务。
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

    @Transactional
    public int cleanup(AuditCleanupRequest request) {
        requireAdmin();
        LocalDateTime before = AuditCleanupSupport.resolveBefore(request);
        return before == null ? mapper.deleteAll() : mapper.deleteBefore(before);
    }

    private void requireAdmin() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可管理模型日志");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
