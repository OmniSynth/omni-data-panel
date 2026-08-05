package com.omni.panel.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.omni.panel.entity.RowRuleEntity;

/**
 * 数据集字段白名单与行级过滤策略持久化接口，映射 {@code bi_field_permission}、
 * {@code bi_row_rule} 表。
 *
 * <p>由 {@link com.omni.panel.service.DataPolicyService} 维护策略配置，并由
 * {@link com.omni.panel.service.QueryService} 在查询编译时合并执行。查询侧以
 * {@link #fieldRuleCount(long, long)} 判断用户是否已配置字段策略：没有任何配置时不限制字段；
 * 一旦存在配置，则仅 {@link #allowedFields(long, long)} 返回的字段可访问，其他字段默认拒绝。
 * 行级规则同时返回用户专属规则和全局规则。</p>
 */
public interface DataPolicyMapper {
    /**
     * 查询用户在数据集上显式允许的字段。
     *
     * @param datasetId 数据集标识
     * @param userId    用户标识
     * @return 允许访问的语义字段名
     */
    @Select("""
            SELECT field_name FROM bi_field_permission
            WHERE dataset_id = #{datasetId} AND user_id = #{userId} AND allowed = TRUE
            """)
    List<String> allowedFields(long datasetId, long userId);

    /**
     * 统计用户在数据集上的全部字段配置，用于启用“有配置则默认拒绝”的白名单模式。
     *
     * @param datasetId 数据集标识
     * @param userId    用户标识
     * @return 字段权限配置数量
     */
    @Select("""
            SELECT COUNT(*) FROM bi_field_permission
            WHERE dataset_id = #{datasetId} AND user_id = #{userId}
            """)
    int fieldRuleCount(long datasetId, long userId);

    /**
     * 列出用户在数据集上的字段权限配置。
     *
     * @param datasetId 数据集标识
     * @param userId    用户标识
     * @return 字段权限行
     */
    @Select("""
            SELECT field_name AS fieldName, allowed
            FROM bi_field_permission
            WHERE dataset_id = #{datasetId} AND user_id = #{userId}
            ORDER BY field_name
            """)
    List<FieldPermissionRow> listFieldPermissions(long datasetId, long userId);

    /**
     * 查询数据集上已启用的用户专属规则和全局行级规则。
     *
     * @param datasetId 数据集标识
     * @param userId    用户标识
     * @return 按规则标识排序的过滤节点 JSON
     */
    @Select("""
            SELECT rule_json FROM bi_row_rule
            WHERE dataset_id = #{datasetId} AND (user_id = #{userId} OR user_id IS NULL) AND enabled = TRUE
            ORDER BY id
            """)
    List<String> rowRules(long datasetId, long userId);

    /**
     * 列出数据集上的全部行级规则（含禁用）。
     *
     * @param datasetId 数据集标识
     * @return 行级规则实体
     */
    @Select("""
            SELECT id, dataset_id AS datasetId, user_id AS userId, name, rule_json AS ruleJson, enabled
            FROM bi_row_rule
            WHERE dataset_id = #{datasetId}
            ORDER BY id
            """)
    List<RowRuleEntity> listRowRules(long datasetId);

    /**
     * 按标识与数据集加载行级规则。
     *
     * @param id        规则标识
     * @param datasetId 数据集标识
     * @return 规则实体；不存在时为 {@code null}
     */
    @Select("""
            SELECT id, dataset_id AS datasetId, user_id AS userId, name, rule_json AS ruleJson, enabled
            FROM bi_row_rule
            WHERE id = #{id} AND dataset_id = #{datasetId}
            """)
    RowRuleEntity findRowRule(long id, long datasetId);

    /**
     * 保存字段允许或拒绝配置，已存在时覆盖允许状态。
     *
     * @param datasetId 数据集标识
     * @param userId    用户标识
     * @param fieldName 语义字段名
     * @param allowed   是否允许访问
     * @return 受影响的记录数
     */
    @Insert("""
            INSERT INTO bi_field_permission(dataset_id, user_id, field_name, allowed)
            VALUES(#{datasetId}, #{userId}, #{fieldName}, #{allowed})
            ON DUPLICATE KEY UPDATE allowed = VALUES(allowed)
            """)
    int saveFieldPermission(long datasetId, long userId, String fieldName, boolean allowed);

    /**
     * 删除用户对单个字段的显式配置。
     *
     * @param datasetId 数据集标识
     * @param userId    用户标识
     * @param fieldName 语义字段名
     * @return 受影响的记录数
     */
    @Delete("""
            DELETE FROM bi_field_permission
            WHERE dataset_id = #{datasetId} AND user_id = #{userId} AND field_name = #{fieldName}
            """)
    int deleteFieldPermission(long datasetId, long userId, String fieldName);

    /**
     * 删除用户在数据集上的全部字段权限配置。
     *
     * @param datasetId 数据集标识
     * @param userId    用户标识
     * @return 受影响的记录数
     */
    @Delete("""
            DELETE FROM bi_field_permission
            WHERE dataset_id = #{datasetId} AND user_id = #{userId}
            """)
    int deleteAllFieldPermissions(long datasetId, long userId);

    /**
     * 新增已启用的用户专属或全局行级规则，并回填生成主键。
     *
     * @param rule 规则实体（写入后填充 id）
     * @return 受影响的记录数
     */
    @Insert("""
            INSERT INTO bi_row_rule(dataset_id, user_id, name, rule_json, enabled)
            VALUES(#{datasetId}, #{userId}, #{name}, #{ruleJson}, #{enabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRowRule(RowRuleEntity rule);

    /**
     * 更新行级规则内容。
     *
     * @param rule 含标识与数据集的规则实体
     * @return 受影响的记录数
     */
    @Update("""
            UPDATE bi_row_rule
            SET user_id = #{userId}, name = #{name}, rule_json = #{ruleJson}, enabled = #{enabled}
            WHERE id = #{id} AND dataset_id = #{datasetId}
            """)
    int updateRowRule(RowRuleEntity rule);

    /**
     * 按规则和数据集联合条件删除行级规则，避免跨数据集删除。
     *
     * @param id        行级规则标识
     * @param datasetId 数据集标识
     * @return 受影响的记录数
     */
    @Delete("DELETE FROM bi_row_rule WHERE id = #{id} AND dataset_id = #{datasetId}")
    int deleteRowRule(long id, long datasetId);

    /**
     * 字段权限查询行。
     *
     * @param fieldName 语义字段名
     * @param allowed   是否允许
     */
    record FieldPermissionRow(String fieldName, boolean allowed) {
    }
}
