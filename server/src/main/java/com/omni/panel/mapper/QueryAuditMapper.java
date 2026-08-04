package com.omni.panel.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 查询审计持久化与管理端检索，映射 {@code bi_query_audit} 表。
 *
 * <p>由 {@link QueryAuditService} 记录查询生命周期，并提供管理端分页检索与历史清理。</p>
 */
public interface QueryAuditMapper {
    /**
     * 创建运行中的查询审计记录。
     *
     * @param queryId   查询唯一标识
     * @param userId    发起用户标识
     * @param sourceId  数据源标识
     * @param sql       执行的 SQL 文本
     * @param clientIp  客户端 IP
     * @param userAgent 客户端 User-Agent
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO bi_query_audit(
                query_id, user_id, data_source_id, sql_text, client_ip, user_agent,
                status, started_at)
            VALUES(
                #{queryId}, #{userId}, #{sourceId}, #{sql}, #{clientIp}, #{userAgent},
                'RUNNING', CURRENT_TIMESTAMP)
            """)
    int start(@Param("queryId") String queryId,
              @Param("userId") long userId,
              @Param("sourceId") long sourceId,
              @Param("sql") String sql,
              @Param("clientIp") String clientIp,
              @Param("userAgent") String userAgent);

    /**
     * 记录查询最终状态、行数、耗时、预览或失败信息。
     *
     * @param queryId       查询唯一标识
     * @param status        最终状态
     * @param rowCount      返回行数；失败或未统计时可为空
     * @param error         错误信息；成功时可为空
     * @param durationMs    执行耗时（毫秒）；未统计时可为空
     * @param resultPreview 结果预览 JSON；未生成时可为空
     * @return 更新行数
     */
    @Update("""
            UPDATE bi_query_audit
            SET status = #{status},
                row_count = #{rowCount},
                error_message = #{error},
                duration_ms = #{durationMs},
                result_preview = #{resultPreview},
                finished_at = CURRENT_TIMESTAMP
            WHERE query_id = #{queryId}
            """)
    int finish(@Param("queryId") String queryId,
               @Param("status") String status,
               @Param("rowCount") Integer rowCount,
               @Param("error") String error,
               @Param("durationMs") Long durationMs,
               @Param("resultPreview") String resultPreview);

    /**
     * 按关键字、状态、用户、数据源与时间范围统计查询审计记录数。
     *
     * @param keyword  关键字；匹配 SQL、查询标识、用户名、显示名、数据源名或 IP，为空时不限制
     * @param status   查询状态；为空时不限制
     * @param userId   用户标识；为空时不限制
     * @param sourceId 数据源标识；为空时不限制
     * @param fromTime 起始时间（含）；为空时不限制
     * @param toTime   截止时间（含）；为空时不限制
     * @return 符合条件的记录总数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM bi_query_audit a
            INNER JOIN sys_user u ON u.id = a.user_id
            INNER JOIN bi_data_source d ON d.id = a.data_source_id
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (a.sql_text LIKE CONCAT('%', #{keyword}, '%')
                   OR a.query_id LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.display_name LIKE CONCAT('%', #{keyword}, '%')
                   OR d.name LIKE CONCAT('%', #{keyword}, '%')
                   OR a.client_ip LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null and status != ''">
              AND a.status = #{status}
            </if>
            <if test="userId != null">
              AND a.user_id = #{userId}
            </if>
            <if test="sourceId != null">
              AND a.data_source_id = #{sourceId}
            </if>
            <if test="fromTime != null">
              AND a.started_at &gt;= #{fromTime}
            </if>
            <if test="toTime != null">
              AND a.started_at &lt;= #{toTime}
            </if>
            </script>
            """)
    long count(@Param("keyword") String keyword,
               @Param("status") String status,
               @Param("userId") Long userId,
               @Param("sourceId") Long sourceId,
               @Param("fromTime") LocalDateTime fromTime,
               @Param("toTime") LocalDateTime toTime);

    /**
     * 分页查询查询审计列表，按开始时间倒序排列。
     *
     * @param keyword  关键字；匹配 SQL、查询标识、用户名、显示名、数据源名或 IP，为空时不限制
     * @param status   查询状态；为空时不限制
     * @param userId   用户标识；为空时不限制
     * @param sourceId 数据源标识；为空时不限制
     * @param fromTime 起始时间（含）；为空时不限制
     * @param toTime   截止时间（含）；为空时不限制
     * @param offset   分页偏移量
     * @param limit    每页条数
     * @return 审计行列表
     */
    @Select("""
            <script>
            SELECT a.id AS id,
                   a.query_id AS queryId,
                   a.user_id AS userId,
                   u.username AS username,
                   u.display_name AS displayName,
                   a.data_source_id AS dataSourceId,
                   d.name AS dataSourceName,
                   a.sql_text AS sqlText,
                   a.client_ip AS clientIp,
                   a.user_agent AS userAgent,
                   a.status AS status,
                   a.row_count AS rowCount,
                   a.error_message AS errorMessage,
                   a.duration_ms AS durationMs,
                   a.result_preview AS resultPreview,
                   a.started_at AS startedAt,
                   a.finished_at AS finishedAt
            FROM bi_query_audit a
            INNER JOIN sys_user u ON u.id = a.user_id
            INNER JOIN bi_data_source d ON d.id = a.data_source_id
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (a.sql_text LIKE CONCAT('%', #{keyword}, '%')
                   OR a.query_id LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.display_name LIKE CONCAT('%', #{keyword}, '%')
                   OR d.name LIKE CONCAT('%', #{keyword}, '%')
                   OR a.client_ip LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null and status != ''">
              AND a.status = #{status}
            </if>
            <if test="userId != null">
              AND a.user_id = #{userId}
            </if>
            <if test="sourceId != null">
              AND a.data_source_id = #{sourceId}
            </if>
            <if test="fromTime != null">
              AND a.started_at &gt;= #{fromTime}
            </if>
            <if test="toTime != null">
              AND a.started_at &lt;= #{toTime}
            </if>
            ORDER BY a.started_at DESC, a.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AuditRow> list(@Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("userId") Long userId,
                        @Param("sourceId") Long sourceId,
                        @Param("fromTime") LocalDateTime fromTime,
                        @Param("toTime") LocalDateTime toTime,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

    /**
     * 按主键查询单条查询审计详情。
     *
     * @param id 审计记录标识
     * @return 审计行；不存在时返回 {@code null}
     */
    @Select("""
            SELECT a.id AS id,
                   a.query_id AS queryId,
                   a.user_id AS userId,
                   u.username AS username,
                   u.display_name AS displayName,
                   a.data_source_id AS dataSourceId,
                   d.name AS dataSourceName,
                   a.sql_text AS sqlText,
                   a.client_ip AS clientIp,
                   a.user_agent AS userAgent,
                   a.status AS status,
                   a.row_count AS rowCount,
                   a.error_message AS errorMessage,
                   a.duration_ms AS durationMs,
                   a.result_preview AS resultPreview,
                   a.started_at AS startedAt,
                   a.finished_at AS finishedAt
            FROM bi_query_audit a
            INNER JOIN sys_user u ON u.id = a.user_id
            INNER JOIN bi_data_source d ON d.id = a.data_source_id
            WHERE a.id = #{id}
            """)
    AuditRow findById(long id);

    /**
     * 清空全部查询审计记录。
     *
     * @return 删除行数
     */
    @Delete("DELETE FROM bi_query_audit")
    int deleteAll();

    /**
     * 删除指定时间之前的历史查询审计记录。
     *
     * @param before 截止时间（不含）
     * @return 删除行数
     */
    @Delete("DELETE FROM bi_query_audit WHERE started_at < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);

    /**
     * 审计列表/详情行。
     *
     * @param id             记录标识
     * @param queryId        查询唯一标识
     * @param userId         发起用户标识
     * @param username       用户名
     * @param displayName    用户显示名
     * @param dataSourceId   数据源标识
     * @param dataSourceName 数据源名称
     * @param sqlText        SQL 文本
     * @param clientIp       客户端 IP
     * @param userAgent      客户端 User-Agent
     * @param status         查询状态
     * @param rowCount       返回行数
     * @param errorMessage   错误信息
     * @param durationMs     执行耗时（毫秒）
     * @param resultPreview  结果预览 JSON
     * @param startedAt      开始时间
     * @param finishedAt     结束时间
     */
    record AuditRow(
            Long id,
            String queryId,
            Long userId,
            String username,
            String displayName,
            Long dataSourceId,
            String dataSourceName,
            String sqlText,
            String clientIp,
            String userAgent,
            String status,
            Integer rowCount,
            String errorMessage,
            Long durationMs,
            String resultPreview,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }
}
