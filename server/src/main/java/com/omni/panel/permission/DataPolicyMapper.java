package com.omni.panel.permission;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 数据集字段白名单与行级过滤策略持久化接口，映射 {@code bi_field_permission}、
 * {@code bi_row_rule} 表。
 *
 * <p>供 {@link com.omni.panel.permission.DataPolicyService} 配置数据访问策略，
 * 并由查询编译器在运行时合并执行。查询侧以 {@link #fieldRuleCount(long, long)} 判断用户是否已配置
 * 字段策略：没有任何配置时不限制字段；一旦存在配置，则仅 {@link #allowedFields(long, long)}
 * 返回的字段可访问，其他字段默认拒绝。行级规则同时返回用户专属规则和全局规则。</p>
 */
public interface DataPolicyMapper {
    /**
     * 查询用户在数据集上显式允许的字段。
     *
     * @param datasetId 数据集标识
     * @param userId 用户标识
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
     * @param userId 用户标识
     * @return 字段权限配置数量
     */
    @Select("""
        SELECT COUNT(*) FROM bi_field_permission
        WHERE dataset_id = #{datasetId} AND user_id = #{userId}
        """)
    int fieldRuleCount(long datasetId, long userId);

    /**
     * 查询数据集上已启用的用户专属规则和全局行级规则。
     *
     * @param datasetId 数据集标识
     * @param userId 用户标识
     * @return 按规则标识排序的过滤节点 JSON
     */
    @Select("""
        SELECT rule_json FROM bi_row_rule
        WHERE dataset_id = #{datasetId} AND (user_id = #{userId} OR user_id IS NULL) AND enabled = TRUE
        ORDER BY id
        """)
    List<String> rowRules(long datasetId, long userId);

    /**
     * 保存字段允许或拒绝配置，已存在时覆盖允许状态。
     *
     * @param datasetId 数据集标识
     * @param userId 用户标识
     * @param fieldName 语义字段名
     * @param allowed 是否允许访问
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
     * @param userId 用户标识
     * @param fieldName 语义字段名
     * @return 受影响的记录数
     */
    @Delete("""
        DELETE FROM bi_field_permission
        WHERE dataset_id = #{datasetId} AND user_id = #{userId} AND field_name = #{fieldName}
        """)
    int deleteFieldPermission(long datasetId, long userId, String fieldName);

    /**
     * 新增已启用的用户专属或全局行级规则。
     *
     * @param datasetId 数据集标识
     * @param userId 用户标识；为空表示全局规则
     * @param name 规则名称
     * @param ruleJson 过滤节点 JSON
     * @return 受影响的记录数
     */
    @Insert("""
        INSERT INTO bi_row_rule(dataset_id, user_id, name, rule_json, enabled)
        VALUES(#{datasetId}, #{userId}, #{name}, #{ruleJson}, TRUE)
        """)
    int insertRowRule(long datasetId, Long userId, String name, String ruleJson);

    /**
     * 按规则和数据集联合条件删除行级规则，避免跨数据集删除。
     *
     * @param id 行级规则标识
     * @param datasetId 数据集标识
     * @return 受影响的记录数
     */
    @Delete("DELETE FROM bi_row_rule WHERE id = #{id} AND dataset_id = #{datasetId}")
    int deleteRowRule(long id, long datasetId);
}
