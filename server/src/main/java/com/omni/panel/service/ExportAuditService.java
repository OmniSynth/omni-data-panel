package com.omni.panel.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.AuditCleanupRequest;
import com.omni.panel.common.AuditCleanupSupport;
import com.omni.panel.common.BusinessException;
import com.omni.panel.common.ClientRequestInfo;
import com.omni.panel.common.PageResult;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.mapper.ExportAuditMapper;

/**
 * 导出审计写入与管理端检索清理。
 */
@Service
public class ExportAuditService {
    private static final Logger log = LoggerFactory.getLogger(ExportAuditService.class);
    private static final Set<String> STATUSES = Set.of("SUCCEEDED", "FAILED");
    private static final Set<String> MODES = Set.of("SYNC", "ASYNC");
    private static final Set<String> FORMATS = Set.of("CSV", "XLSX");

    private final ExportAuditMapper mapper;
    private final SettingService settingService;

    /**
     * @param mapper         导出审计持久化
     * @param settingService 系统设置
     */
    public ExportAuditService(ExportAuditMapper mapper, SettingService settingService) {
        this.mapper = mapper;
        this.settingService = settingService;
    }

    /**
     * 记录一次导出事件；失败不影响主业务。
     *
     * @param userId       操作用户
     * @param queryId      查询标识
     * @param dataSourceId 数据源标识
     * @param format       CSV / XLSX
     * @param mode         SYNC / ASYNC
     * @param status       SUCCEEDED / FAILED
     * @param rowCount     导出行数
     * @param byteSize     文件字节数
     * @param taskId       异步任务标识
     * @param client       客户端信息
     * @param errorMessage 失败原因
     */
    public void record(Long userId,
                       String queryId,
                       Long dataSourceId,
                       String format,
                       String mode,
                       String status,
                       Integer rowCount,
                       Long byteSize,
                       String taskId,
                       ClientRequestInfo.Info client,
                       String errorMessage) {
        if (userId == null) {
            return;
        }
        String normalizedFormat = normalizeToken(format, FORMATS);
        String normalizedMode = normalizeToken(mode, MODES);
        String normalizedStatus = normalizeToken(status, STATUSES);
        if (normalizedFormat == null || normalizedMode == null || normalizedStatus == null) {
            return;
        }
        try {
            String safeQueryId = truncate(queryId, 36);
            String safeTaskId = truncate(taskId, 36);
            String safeError = truncate(errorMessage, 1000);
            String clientIp = client == null ? null : truncate(client.clientIp(), 64);
            String userAgent = client == null ? null : truncate(client.userAgent(), 512);
            mapper.insert(
                    userId,
                    safeQueryId,
                    dataSourceId,
                    normalizedFormat,
                    normalizedMode,
                    normalizedStatus,
                    rowCount,
                    byteSize,
                    safeTaskId,
                    clientIp,
                    userAgent,
                    safeError);
        } catch (RuntimeException ex) {
            log.warn("写入导出审计失败: queryId={}, format={}, status={}", queryId, format, status, ex);
        }
    }

    /**
     * 分页检索导出审计（仅管理员）。
     */
    public PageResult<ExportAuditMapper.AuditRow> page(String keyword, String status, String format,
                                                       LocalDateTime fromTime, LocalDateTime toTime,
                                                       int page, int size) {
        requireAdmin();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedKeyword = blankToNull(keyword);
        String normalizedStatus = normalizeToken(status, STATUSES);
        String normalizedFormat = normalizeToken(format, FORMATS);
        long total = mapper.count(normalizedKeyword, normalizedStatus, normalizedFormat, fromTime, toTime);
        List<ExportAuditMapper.AuditRow> items = total == 0
                ? List.of()
                : mapper.list(normalizedKeyword, normalizedStatus, normalizedFormat, fromTime, toTime, offset, safeSize);
        return new PageResult<>(items, total, safePage, safeSize);
    }

    /**
     * 按条件清理导出审计（仅管理员）。
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
            throw new BusinessException(403, "仅管理员可管理导出日志");
        }
    }

    private static String normalizeToken(String value, Set<String> allowed) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        String normalized = blankToNull(value);
        if (normalized == null || normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, max);
    }
}
