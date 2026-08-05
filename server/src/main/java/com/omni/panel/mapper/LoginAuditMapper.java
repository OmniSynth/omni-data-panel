package com.omni.panel.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 登录审计持久化与管理端查询，映射 {@code sys_login_audit} 表。
 *
 * <p>由 {@link LoginAuditService} 写入登录尝试记录，并提供管理端分页检索与历史清理。</p>
 */
public interface LoginAuditMapper {
    /**
     * 写入一条登录审计记录。
     *
     * @param username  尝试登录的用户名
     * @param userId    匹配到的用户标识；未匹配用户时可为空
     * @param success   是否登录成功
     * @param message   结果说明或失败原因
     * @param clientIp  客户端 IP
     * @param userAgent 客户端 User-Agent
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO sys_login_audit(username, user_id, success, message, client_ip, user_agent, logged_at)
            VALUES(#{username}, #{userId}, #{success}, #{message}, #{clientIp}, #{userAgent}, CURRENT_TIMESTAMP)
            """)
    int insert(@Param("username") String username,
               @Param("userId") Long userId,
               @Param("success") boolean success,
               @Param("message") String message,
               @Param("clientIp") String clientIp,
               @Param("userAgent") String userAgent);

    /**
     * 按关键字、成败状态与时间范围统计登录审计记录数。
     *
     * @param keyword  关键字；匹配用户名、IP、消息或 User-Agent，为空时不限制
     * @param success  成败筛选；为空时不限制
     * @param fromTime 起始时间（含）；为空时不限制
     * @param toTime   截止时间（含）；为空时不限制
     * @return 符合条件的记录总数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM sys_login_audit a
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (a.username LIKE CONCAT('%', #{keyword}, '%')
                   OR a.client_ip LIKE CONCAT('%', #{keyword}, '%')
                   OR a.message LIKE CONCAT('%', #{keyword}, '%')
                   OR a.user_agent LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="success != null">
              AND a.success = #{success}
            </if>
            <if test="fromTime != null">
              AND a.logged_at &gt;= #{fromTime}
            </if>
            <if test="toTime != null">
              AND a.logged_at &lt;= #{toTime}
            </if>
            </script>
            """)
    long count(@Param("keyword") String keyword,
               @Param("success") Boolean success,
               @Param("fromTime") LocalDateTime fromTime,
               @Param("toTime") LocalDateTime toTime);

    /**
     * 分页查询登录审计列表，按登录时间倒序排列。
     *
     * @param keyword  关键字；匹配用户名、IP、消息或 User-Agent，为空时不限制
     * @param success  成败筛选；为空时不限制
     * @param fromTime 起始时间（含）；为空时不限制
     * @param toTime   截止时间（含）；为空时不限制
     * @param offset   分页偏移量
     * @param limit    每页条数
     * @return 登录审计行列表
     */
    @Select("""
            <script>
            SELECT a.id AS id,
                   a.username AS username,
                   a.user_id AS userId,
                   a.success AS success,
                   a.message AS message,
                   a.client_ip AS clientIp,
                   a.user_agent AS userAgent,
                   a.logged_at AS loggedAt
            FROM sys_login_audit a
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (a.username LIKE CONCAT('%', #{keyword}, '%')
                   OR a.client_ip LIKE CONCAT('%', #{keyword}, '%')
                   OR a.message LIKE CONCAT('%', #{keyword}, '%')
                   OR a.user_agent LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="success != null">
              AND a.success = #{success}
            </if>
            <if test="fromTime != null">
              AND a.logged_at &gt;= #{fromTime}
            </if>
            <if test="toTime != null">
              AND a.logged_at &lt;= #{toTime}
            </if>
            ORDER BY a.logged_at DESC, a.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<LoginRow> list(@Param("keyword") String keyword,
                        @Param("success") Boolean success,
                        @Param("fromTime") LocalDateTime fromTime,
                        @Param("toTime") LocalDateTime toTime,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

    /**
     * 清空全部登录审计记录。
     *
     * @return 删除行数
     */
    @Delete("DELETE FROM sys_login_audit")
    int deleteAll();

    /**
     * 删除指定时间之前的历史登录审计记录。
     *
     * @param before 截止时间（不含）
     * @return 删除行数
     */
    @Delete("DELETE FROM sys_login_audit WHERE logged_at < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);

    /**
     * 登录审计列表行。
     *
     * @param id        记录标识
     * @param username  尝试登录的用户名
     * @param userId    匹配到的用户标识
     * @param success   是否登录成功
     * @param message   结果说明或失败原因
     * @param clientIp  客户端 IP
     * @param userAgent 客户端 User-Agent
     * @param loggedAt  登录时间
     */
    record LoginRow(
            Long id,
            String username,
            Long userId,
            Boolean success,
            String message,
            String clientIp,
            String userAgent,
            LocalDateTime loggedAt
    ) {
    }
}
