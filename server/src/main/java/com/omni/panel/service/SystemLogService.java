package com.omni.panel.service;

import org.springframework.stereotype.Service;
import com.omni.panel.common.BusinessException;
import com.omni.panel.common.PageResult;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.logging.SystemLogBuffer;

/**
 * 管理端读取进程内系统日志缓冲。
 */
@Service
public class SystemLogService {
    private final SettingService settingService;

    /**
     * 注入系统设置服务。
     *
     * @param settingService 系统设置
     */
    public SystemLogService(SettingService settingService) {
        this.settingService = settingService;
    }

    /**
     * 分页检索进程内系统日志（仅管理员）。
     *
     * @param keyword 关键词
     * @param level   日志级别
     * @param page    页码（从 1 起）
     * @param size    每页条数
     * @return 分页结果
     */
    public PageResult<SystemLogBuffer.Entry> page(String keyword, String level, int page, int size) {
        requireAdmin();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), SystemLogBuffer.CAPACITY);
        SystemLogBuffer.Page result = SystemLogBuffer.get().page(keyword, level, safePage, safeSize);
        return new PageResult<>(result.items(), result.total(), result.page(), result.size());
    }

    /**
     * 返回系统日志缓冲容量与当前条数（仅管理员）。
     *
     * @return 缓冲元信息
     */
    public SystemLogMeta meta() {
        requireAdmin();
        SystemLogBuffer.Page snapshot = SystemLogBuffer.get().page(null, null, 1, 1);
        return new SystemLogMeta(snapshot.capacity(), snapshot.buffered());
    }

    /**
     * 清空进程内系统日志缓冲（仅管理员，且需开启清空日志配置）。
     */
    public void clear() {
        requireAdmin();
        settingService.requireLogsClearEnabled();
        SystemLogBuffer.get().clear();
    }

    /**
     * 要求当前用户为管理员。
     */
    private void requireAdmin() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可查看系统日志");
        }
    }

    /**
     * 系统日志缓冲元信息。
     *
     * @param capacity 缓冲容量上限
     * @param buffered 当前缓冲条数
     */
    public record SystemLogMeta(int capacity, int buffered) {
    }
}
