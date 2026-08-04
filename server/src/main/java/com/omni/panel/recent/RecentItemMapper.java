package com.omni.panel.recent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 最近访问项持久化接口，映射 {@code bi_recent_item} 表。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，由 {@link RecentService} 记录并展示用户最近打开的资源。</p>
 */
public interface RecentItemMapper extends BaseMapper<RecentItemEntity> {
    /**
     * 写入或刷新最近访问时间；同一用户与资源已存在时仅更新访问时间。
     *
     * @param userId 用户标识
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @return 影响行数
     */
    @Insert("""
        INSERT INTO bi_recent_item(user_id, resource_type, resource_id, visited_at)
        VALUES(#{userId}, #{resourceType}, #{resourceId}, NOW())
        ON DUPLICATE KEY UPDATE visited_at = NOW()
        """)
    int touch(@Param("userId") long userId,
              @Param("resourceType") String resourceType,
              @Param("resourceId") long resourceId);
}
