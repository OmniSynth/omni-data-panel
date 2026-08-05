package com.omni.panel.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.mapper.MetadataMapper;
import com.omni.panel.service.MetadataService;

/**
 * 提供数据源元数据同步及模式、表、字段查询接口。
 */
@RestController
@RequestMapping("/api/data-sources/{sourceId}/metadata")
public class MetadataController {
    private final MetadataService service;

    public MetadataController(MetadataService service) {
        this.service = service;
    }

    /**
     * 从指定数据源重新同步元数据快照。
     *
     * @param sourceId 数据源标识
     * @return 无数据的成功响应
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> sync(@PathVariable long sourceId) {
        service.sync(sourceId);
        return ApiResponse.ok();
    }

    /**
     * 查询指定数据源已同步的模式名称。
     *
     * @param sourceId 数据源标识
     * @return 模式名称列表
     */
    @GetMapping("/schemas")
    public ApiResponse<List<String>> schemas(@PathVariable long sourceId) {
        return ApiResponse.ok(service.schemas(sourceId));
    }

    /**
     * 查询指定模式下已同步的表与视图。
     *
     * @param sourceId 数据源标识
     * @param schema   模式名称
     * @return 表视图列表
     */
    @GetMapping("/schemas/{schema}/tables")
    public ApiResponse<List<MetadataMapper.TableView>> tables(
            @PathVariable long sourceId, @PathVariable String schema) {
        return ApiResponse.ok(service.tables(sourceId, schema));
    }

    /**
     * 查询指定表已同步的字段。
     *
     * @param sourceId 数据源标识
     * @param schema   模式名称
     * @param table    表名称
     * @return 字段视图列表
     */
    @GetMapping("/schemas/{schema}/tables/{table}/columns")
    public ApiResponse<List<MetadataMapper.ColumnView>> columns(
            @PathVariable long sourceId, @PathVariable String schema, @PathVariable String table) {
        return ApiResponse.ok(service.columns(sourceId, schema, table));
    }

    /**
     * 查询 SQL 编辑器补全所需的方言与表字段目录。
     *
     * @param sourceId 数据源标识
     * @return 补全目录
     */
    @GetMapping("/completion-schema")
    public ApiResponse<MetadataService.CompletionSchema> completionSchema(@PathVariable long sourceId) {
        return ApiResponse.ok(service.completionSchema(sourceId));
    }
}
