package com.omni.panel.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.AuditCleanupRequest;
import com.omni.panel.common.AuditCleanupSupport;
import com.omni.panel.common.BusinessException;
import com.omni.panel.common.PageResult;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.mapper.QueryAuditMapper;

/**
 * 管理端查询审计检索与清理。
 */
@Service
public class QueryAuditService {
    private final QueryAuditMapper mapper;
    private final SettingService settingService;

    /**
     * 注入查询审计数据访问与系统设置。
     *
     * @param mapper         查询审计持久化
     * @param settingService 系统设置
     */
    public QueryAuditService(QueryAuditMapper mapper, SettingService settingService) {
        this.mapper = mapper;
        this.settingService = settingService;
    }

    /**
     * 分页查询审计记录。
     */
    public PageResult<QueryAuditMapper.AuditRow> page(String keyword, String status, Long userId, Long sourceId,
                                                      LocalDateTime fromTime, LocalDateTime toTime,
                                                      int page, int size) {
        requireAdmin();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedStatus = blankToNull(status);
        String normalizedKeyword = blankToNull(keyword);
        long total = mapper.count(normalizedKeyword, normalizedStatus, userId, sourceId, fromTime, toTime);
        List<QueryAuditMapper.AuditRow> items = total == 0
                ? List.of()
                : mapper.list(normalizedKeyword, normalizedStatus, userId, sourceId, fromTime, toTime, offset, safeSize);
        return new PageResult<>(items, total, safePage, safeSize);
    }

    /**
     * 查询审计详情。
     */
    public QueryAuditMapper.AuditRow require(long id) {
        requireAdmin();
        QueryAuditMapper.AuditRow row = mapper.findById(id);
        if (row == null) {
            throw new BusinessException(404, "审计记录不存在");
        }
        return row;
    }

    /**
     * 按模式清理查询审计。
     *
     * @return 删除条数
     */
    @Transactional
    public int cleanup(AuditCleanupRequest request) {
        requireAdmin();
        settingService.requireLogsClearEnabled();
        LocalDateTime before = AuditCleanupSupport.resolveBefore(request);
        return before == null ? mapper.deleteAll() : mapper.deleteBefore(before);
    }

    /**
     * 校验当前用户为管理员，否则拒绝访问。
     */
    private void requireAdmin() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可管理查询审计");
        }
    }

    /**
     * 将空白字符串规范化为 null，便于 SQL 条件省略可选过滤项。
     *
     * @param value 原始字符串
     * @return 去首尾空白后的非空字符串，或 null
     */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
