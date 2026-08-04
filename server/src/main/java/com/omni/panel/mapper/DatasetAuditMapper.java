package com.omni.panel.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 模型（数据集）变更审计，映射 {@code bi_dataset_audit}。
 */
public interface DatasetAuditMapper {
    @Insert("""
            INSERT INTO bi_dataset_audit(dataset_id, dataset_name, action, operator_id, detail, created_at)
            VALUES(#{datasetId}, #{datasetName}, #{action}, #{operatorId}, #{detail}, CURRENT_TIMESTAMP)
            """)
    int insert(@Param("datasetId") Long datasetId,
               @Param("datasetName") String datasetName,
               @Param("action") String action,
               @Param("operatorId") Long operatorId,
               @Param("detail") String detail);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM bi_dataset_audit a
            LEFT JOIN sys_user u ON u.id = a.operator_id
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (a.dataset_name LIKE CONCAT('%', #{keyword}, '%')
                   OR a.detail LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.display_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="action != null and action != ''">
              AND a.action = #{action}
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
               @Param("action") String action,
               @Param("fromTime") LocalDateTime fromTime,
               @Param("toTime") LocalDateTime toTime);

    @Select("""
            <script>
            SELECT a.id AS id,
                   a.dataset_id AS datasetId,
                   a.dataset_name AS datasetName,
                   a.action AS action,
                   a.operator_id AS operatorId,
                   u.username AS operatorUsername,
                   u.display_name AS operatorDisplayName,
                   a.detail AS detail,
                   a.created_at AS createdAt
            FROM bi_dataset_audit a
            LEFT JOIN sys_user u ON u.id = a.operator_id
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
              AND (a.dataset_name LIKE CONCAT('%', #{keyword}, '%')
                   OR a.detail LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.display_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="action != null and action != ''">
              AND a.action = #{action}
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
                        @Param("action") String action,
                        @Param("fromTime") LocalDateTime fromTime,
                        @Param("toTime") LocalDateTime toTime,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

    @Delete("DELETE FROM bi_dataset_audit")
    int deleteAll();

    @Delete("DELETE FROM bi_dataset_audit WHERE created_at < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);

    record AuditRow(
            Long id,
            Long datasetId,
            String datasetName,
            String action,
            Long operatorId,
            String operatorUsername,
            String operatorDisplayName,
            String detail,
            LocalDateTime createdAt
    ) {
    }
}
