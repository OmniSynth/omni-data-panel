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
    public PageResult<SystemLogBuffer.Entry> page(String keyword, String level, int page, int size) {
        requireAdmin();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), SystemLogBuffer.CAPACITY);
        SystemLogBuffer.Page result = SystemLogBuffer.get().page(keyword, level, safePage, safeSize);
        return new PageResult<>(result.items(), result.total(), result.page(), result.size());
    }

    public SystemLogMeta meta() {
        requireAdmin();
        SystemLogBuffer.Page snapshot = SystemLogBuffer.get().page(null, null, 1, 1);
        return new SystemLogMeta(snapshot.capacity(), snapshot.buffered());
    }

    public void clear() {
        requireAdmin();
        SystemLogBuffer.get().clear();
    }

    private void requireAdmin() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可查看系统日志");
        }
    }

    public record SystemLogMeta(int capacity, int buffered) {
    }
}
