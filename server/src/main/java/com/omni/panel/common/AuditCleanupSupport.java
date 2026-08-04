package com.omni.panel.common;

import java.time.LocalDateTime;

/**
 * 审计清理参数解析。
 */
public final class AuditCleanupSupport {
    private AuditCleanupSupport() {}

    /**
     * @return null 表示清空全部；非 null 表示删除该时间之前的记录
     */
    public static LocalDateTime resolveBefore(AuditCleanupRequest request) {
        if (request == null || request.mode() == null || request.mode().isBlank()) {
            throw new BusinessException("请指定清理模式");
        }
        String mode = request.mode().trim().toUpperCase();
        return switch (mode) {
            case "ALL" -> null;
            case "BEFORE_DAYS" -> {
                if (request.days() == null || request.days() < 1) {
                    throw new BusinessException("清理天数须大于 0");
                }
                yield LocalDateTime.now().minusDays(request.days());
            }
            case "BEFORE_DATE" -> {
                if (request.before() == null) {
                    throw new BusinessException("请指定清理截止日期");
                }
                yield request.before();
            }
            default -> throw new BusinessException("不支持的清理模式：" + request.mode());
        };
    }
}
