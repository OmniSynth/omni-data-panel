package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.panel.entity.ExportTaskEntity;

/**
 * 异步导出任务持久化接口，映射 {@code bi_export_task} 表。
 *
 * <p>继承 MyBatis-Plus {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} 提供任务 CRUD，
 * 供同步/异步导出流程读写任务状态、MinIO 对象路径及错误信息。</p>
 */
public interface ExportTaskMapper extends BaseMapper<ExportTaskEntity> {
}
