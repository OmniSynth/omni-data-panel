package com.omni.panel.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 导出审计，映射 {@code bi_export_audit}。
 */
public interface ExportAuditMapper {
    @Insert("""
            INSERT INTO bi_export_audit(
              user_id, query_id, data_source_id, format, mode, status,
              row_count, byte_size, task_id, client_ip, user_agent, error_message, created_at)
            VALUES(
              #{userId}, #{queryId}, #{dataSourceId}, #{format}, #{mode}, #{status},
              #{rowCount}, #{byteSize}, #{taskId}, #{clientIp}, #{userAgent}, #{errorMessage}, CURRENT_TIMESTAMP)
            """)
    int insert(@Param("userId") Long userId,
               @Param("queryId") String queryId,
               @Param("dataSourceId") Long dataSourceId,
               @Param("format") String format,
               @Param("mode") String mode,
               @Param("status") String status,
               @Param("rowCount") Integer rowCount,
               @Param("byteSize") Long byteSize,
               @Param("taskId") String taskId,
               @Param("clientIp") String clientIp,
               @Param("userAgent") String userAgent,
               @Param("errorMessage") String errorMessage);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM bi_export_audit a
            LEFT JOIN sys_user u ON u.id = a.user_id
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (a.query_id LIKE CONCAT('%', #{keyword}, '%')
                   OR a.task_id LIKE CONCAT('%', #{keyword}, '%')
                   OR a.client_ip LIKE CONCAT('%', #{keyword}, '%')
                   OR a.error_message LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.display_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null and status != ''">
              AND a.status = #{status}
            </if>
            <if test="format != null and format != ''">
              AND a.format = #{format}
            </if>
            <if test="fromTime != null">
              AND a.created_at &gt;= #{fromTime}
            </if>
            <if test="toTime != null">
              AND a.created_at &lt;= #{toTime}
            </if>
            </script>
            """)
    long count(@Param("keyword") String keyword,
               @Param("status") String status,
               @Param("format") String format,
               @Param("fromTime") LocalDateTime fromTime,
               @Param("toTime") LocalDateTime toTime);

    @Select("""
            <script>
            SELECT a.id AS id,
                   a.user_id AS userId,
                   u.username AS username,
                   u.display_name AS displayName,
                   a.query_id AS queryId,
                   a.data_source_id AS dataSourceId,
                   ds.name AS dataSourceName,
                   a.format AS format,
                   a.mode AS mode,
                   a.status AS status,
                   a.row_count AS rowCount,
                   a.byte_size AS byteSize,
                   a.task_id AS taskId,
                   a.client_ip AS clientIp,
                   a.user_agent AS userAgent,
                   a.error_message AS errorMessage,
                   a.created_at AS createdAt
            FROM bi_export_audit a
            LEFT JOIN sys_user u ON u.id = a.user_id
            LEFT JOIN bi_data_source ds ON ds.id = a.data_source_id
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (a.query_id LIKE CONCAT('%', #{keyword}, '%')
                   OR a.task_id LIKE CONCAT('%', #{keyword}, '%')
                   OR a.client_ip LIKE CONCAT('%', #{keyword}, '%')
                   OR a.error_message LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.display_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null and status != ''">
              AND a.status = #{status}
            </if>
            <if test="format != null and format != ''">
              AND a.format = #{format}
            </if>
            <if test="fromTime != null">
              AND a.created_at &gt;= #{fromTime}
            </if>
            <if test="toTime != null">
              AND a.created_at &lt;= #{toTime}
            </if>
            ORDER BY a.created_at DESC, a.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AuditRow> list(@Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("format") String format,
                        @Param("fromTime") LocalDateTime fromTime,
                        @Param("toTime") LocalDateTime toTime,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

    @Delete("DELETE FROM bi_export_audit")
    int deleteAll();

    @Delete("DELETE FROM bi_export_audit WHERE created_at < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);

    record AuditRow(
            Long id,
            Long userId,
            String username,
            String displayName,
            String queryId,
            Long dataSourceId,
            String dataSourceName,
            String format,
            String mode,
            String status,
            Integer rowCount,
            Long byteSize,
            String taskId,
            String clientIp,
            String userAgent,
            String errorMessage,
            LocalDateTime createdAt
    ) {
    }
}
