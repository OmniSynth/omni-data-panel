package com.omni.panel.mapper;

import org.apache.ibatis.annotations.Select;

/**
 * 查询各类受 ACL 管理资源的所有者，只读访问 {@code bi_data_source}、{@code bi_dataset}、
 * {@code bi_chart}、{@code bi_dashboard} 表的 {@code owner_id} 字段。
 *
 * <p>供 {@link com.omni.panel.service.PermissionService} 判断资源归属与授权上下文。</p>
 */
public interface ResourceOwnerMapper {
    /**
     * 查询数据源所有者。
     *
     * @param id 数据源标识
     * @return 所有者标识；数据源不存在时返回 {@code null}
     */
    @Select("SELECT owner_id FROM bi_data_source WHERE id = #{id}")
    Long dataSourceOwner(long id);

    /**
     * 查询数据集所有者。
     *
     * @param id 数据集标识
     * @return 所有者标识；数据集不存在时返回 {@code null}
     */
    @Select("SELECT owner_id FROM bi_dataset WHERE id = #{id}")
    Long datasetOwner(long id);

    /**
     * 查询图表所有者。
     *
     * @param id 图表标识
     * @return 所有者标识；图表不存在时返回 {@code null}
     */
    @Select("SELECT owner_id FROM bi_chart WHERE id = #{id}")
    Long chartOwner(long id);

    /**
     * 查询仪表板所有者。
     *
     * @param id 仪表板标识
     * @return 所有者标识；仪表板不存在时返回 {@code null}
     */
    @Select("SELECT owner_id FROM bi_dashboard WHERE id = #{id}")
    Long dashboardOwner(long id);
}
