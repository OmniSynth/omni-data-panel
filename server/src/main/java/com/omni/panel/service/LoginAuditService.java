package com.omni.panel.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.AuditCleanupRequest;
import com.omni.panel.common.AuditCleanupSupport;
import com.omni.panel.common.BusinessException;
import com.omni.panel.common.ClientRequestInfo;
import com.omni.panel.common.PageResult;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.mapper.LoginAuditMapper;

/**
 * 登录审计写入与管理端检索清理。
 */
@Service
public class LoginAuditService {
    private final LoginAuditMapper mapper;
    private final SettingService settingService;

    /**
     * @param mapper         登录审计持久化
     * @param settingService 系统设置
     */
    public LoginAuditService(LoginAuditMapper mapper, SettingService settingService) {
        this.mapper = mapper;
        this.settingService = settingService;
    }

    /**
     * 记录一次登录尝试。
     *
     * @param username 尝试登录的用户名
     * @param userId   成功时对应用户 ID；失败可为 {@code null}
     * @param success  是否登录成功
     * @param message  结果说明
     * @param client   客户端 IP / UA；可为 {@code null}
     */
    public void record(String username, Long userId, boolean success, String message, ClientRequestInfo.Info client) {
        String safeUsername = username == null || username.isBlank() ? "-" : username.trim();
        String safeMessage = message == null || message.isBlank() ? (success ? "登录成功" : "登录失败") : message.trim();
        if (safeMessage.length() > 255) {
            safeMessage = safeMessage.substring(0, 255);
        }
        ClientRequestInfo.Info info = client == null ? new ClientRequestInfo.Info(null, null) : client;
        mapper.insert(safeUsername, userId, success, safeMessage, info.clientIp(), info.userAgent());
    }

    /**
     * 分页检索登录审计（仅管理员）。
     *
     * @param keyword  用户名关键字；可空
     * @param success  按成功/失败过滤；可空
     * @param fromTime 起始时间；可空
     * @param toTime   结束时间；可空
     * @param page     页码（从 1 起）
     * @param size     页大小
     * @return 分页结果
     */
    public PageResult<LoginAuditMapper.LoginRow> page(String keyword, Boolean success,
                                                      LocalDateTime fromTime, LocalDateTime toTime,
                                                      int page, int size) {
        requireAdmin();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedKeyword = blankToNull(keyword);
        long total = mapper.count(normalizedKeyword, success, fromTime, toTime);
        List<LoginAuditMapper.LoginRow> items = total == 0
                ? List.of()
                : mapper.list(normalizedKeyword, success, fromTime, toTime, offset, safeSize);
        return new PageResult<>(items, total, safePage, safeSize);
    }

    /**
     * 按请求清理登录审计；未指定时间则清空全部（仅管理员）。
     *
     * @param request 清理条件
     * @return 删除行数
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
            throw new BusinessException(403, "仅管理员可管理登录日志");
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
