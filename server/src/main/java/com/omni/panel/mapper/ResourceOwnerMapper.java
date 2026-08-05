package com.omni.panel.mapper;

import org.apache.ibatis.annotations.Select;

/**
 * 查询各类受 ACL 管理资源的所有者与集合归属。
 *
 * <p>供 {@link com.omni.panel.service.PermissionService} 判断资源归属、集合继承与授权上下文。</p>
 */
public interface ResourceOwnerMapper {
    /**
     * 查询数据源所有者。
     *
     * @param id 数据源标识
     * @return 所有者标识；不存在时返回 {@code null}
     */
    @Select("SELECT owner_id FROM bi_data_source WHERE id = #{id}")
    Long dataSourceOwner(long id);

    /**
     * 查询数据集所有者。
     *
     * @param id 数据集标识
     * @return 所有者标识；不存在时返回 {@code null}
     */
    @Select("SELECT owner_id FROM bi_dataset WHERE id = #{id} AND deleted_at IS NULL")
    Long datasetOwner(long id);

    /**
     * 查询图表所有者。
     *
     * @param id 图表标识
     * @return 所有者标识；不存在时返回 {@code null}
     */
    @Select("SELECT owner_id FROM bi_chart WHERE id = #{id} AND deleted_at IS NULL")
    Long chartOwner(long id);

    /**
     * 查询仪表板所有者。
     *
     * @param id 仪表板标识
     * @return 所有者标识；不存在时返回 {@code null}
     */
    @Select("SELECT owner_id FROM bi_dashboard WHERE id = #{id} AND deleted_at IS NULL")
    Long dashboardOwner(long id);

    /**
     * 查询指标所有者。
     *
     * @param id 指标标识
     * @return 所有者标识；不存在时返回 {@code null}
     */
    @Select("SELECT owner_id FROM bi_metric WHERE id = #{id} AND deleted_at IS NULL")
    Long metricOwner(long id);

    /**
     * 查询集合所有者。
     *
     * @param id 集合标识
     * @return 所有者标识；不存在或已归档时返回 {@code null}
     */
    @Select("SELECT owner_id FROM bi_collection WHERE id = #{id} AND archived = FALSE")
    Long collectionOwner(long id);

    /**
     * 查询集合的个人空间所有者；非个人集合返回 {@code null}。
     *
     * @param id 集合标识
     * @return 个人所有者标识
     */
    @Select("SELECT personal_owner_id FROM bi_collection WHERE id = #{id} AND archived = FALSE")
    Long collectionPersonalOwner(long id);

    /**
     * 查询集合父标识。
     *
     * @param id 集合标识
     * @return 父集合标识
     */
    @Select("SELECT parent_id FROM bi_collection WHERE id = #{id} AND archived = FALSE")
    Long collectionParentId(long id);

    /**
     * 查询图表所属集合。
     *
     * @param id 图表标识
     * @return 集合标识
     */
    @Select("SELECT collection_id FROM bi_chart WHERE id = #{id} AND deleted_at IS NULL")
    Long chartCollectionId(long id);

    /**
     * 查询仪表盘所属集合。
     *
     * @param id 仪表盘标识
     * @return 集合标识
     */
    @Select("SELECT collection_id FROM bi_dashboard WHERE id = #{id} AND deleted_at IS NULL")
    Long dashboardCollectionId(long id);

    /**
     * 查询数据集所属集合。
     *
     * @param id 数据集标识
     * @return 集合标识
     */
    @Select("SELECT collection_id FROM bi_dataset WHERE id = #{id} AND deleted_at IS NULL")
    Long datasetCollectionId(long id);

    /**
     * 查询指标所属集合。
     *
     * @param id 指标标识
     * @return 集合标识
     */
    @Select("SELECT collection_id FROM bi_metric WHERE id = #{id} AND deleted_at IS NULL")
    Long metricCollectionId(long id);
}
