package com.omni.panel.common;

import java.time.LocalDateTime;

/**
 * 审计日志清理请求。
 *
 * @param mode   清理模式：ALL / BEFORE_DAYS / BEFORE_DATE
 * @param days   BEFORE_DAYS 时的天数（如 3、30）
 * @param before BEFORE_DATE 时的截止时间（删除该时间之前的记录）
 */
public record AuditCleanupRequest(String mode, Integer days, LocalDateTime before) {
}
