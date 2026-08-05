package com.omni.panel.controller;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.DatasetEntity;
import com.omni.panel.entity.DatasetFieldEntity;
import com.omni.panel.service.DatasetService;
import com.omni.panel.service.RecentService;

/**
 * 提供模型及其字段定义的查询和维护接口。
 */
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {
    private final DatasetService service;
    private final RecentService recentService;

    /**
     * 注入模型与最近访问服务。
     *
     * @param service       模型服务
     * @param recentService 最近访问记录服务
     */
    public DatasetController(DatasetService service, RecentService recentService) {
        this.service = service;
        this.recentService = recentService;
    }

    /**
     * 查询当前用户可读取的模型。
     *
     * @return 模型视图列表
     */
    @GetMapping
    public ApiResponse<List<View>> list() {
        return ApiResponse.ok(service.listReadable().stream().map(this::view).toList());
    }

    /**
     * 查询指定模型及其字段定义。
     *
     * @param id 模型标识
     * @return 模型视图
     */
    @GetMapping("/{id}")
    public ApiResponse<View> get(@PathVariable long id) {
        DatasetEntity entity = service.require(id, "READ");
        recentService.touch(AuthenticatedUser.current().id(), "MODEL", id);
        return ApiResponse.ok(view(entity));
    }

    /**
     * 查询模型字段去重取值（只读）。
     *
     * @param id        模型标识
     * @param fieldName 语义字段名
     * @param limit     最大条数，默认 200
     * @return 去重取值列表
     */
    @GetMapping("/{id}/fields/{fieldName}/distinct")
    public ApiResponse<List<Object>> distinctValues(@PathVariable long id,
                                                    @PathVariable String fieldName,
                                                    @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.listDistinctValues(id, fieldName, limit));
    }

    /**
     * 根据定义 SQL 探测结果列并推断输出字段。
     *
     * @param request 数据源与 SQL
     * @return 推断字段列表
     */
    @PostMapping("/infer-sql-fields")
    public ApiResponse<List<DatasetService.InferredField>> inferSqlFields(
            @Valid @RequestBody InferSqlFieldsRequest request) {
        return ApiResponse.ok(service.inferSqlFields(request.dataSourceId(), request.sql()));
    }

    /**
     * 创建模型及字段定义。
     *
     * @param request 模型保存参数
     * @return 已创建的模型视图
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('dataset:create')")
    public ApiResponse<View> create(@Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(view(service.create(toInput(request))));
    }

    /**
     * 更新模型并整体替换字段定义。
     *
     * @param id      模型标识
     * @param request 模型保存参数
     * @return 已更新的模型视图
     */
    @PutMapping("/{id}")
    public ApiResponse<View> update(@PathVariable long id, @Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(view(service.update(id, toInput(request))));
    }

    /**
     * 软删除指定模型。
     *
     * @param id 模型标识
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.softDelete(id);
        return ApiResponse.ok();
    }

    /**
     * 将 HTTP 保存请求转换为服务层输入。
     *
     * @param request 控制器入参
     * @return 服务层保存输入
     */
    private DatasetService.SaveInput toInput(SaveRequest request) {
        return new DatasetService.SaveInput(request.name(), request.description(), request.modelType(),
                request.dataSourceId(), request.schemaName(), request.tableName(), request.definitionSql(),
                request.collectionId(), request.fields());
    }

    /**
     * 将模型实体组装为含字段列表的 API 视图。
     *
     * @param entity 模型实体
     * @return 模型详情视图
     */
    private View view(DatasetEntity entity) {
        return new View(entity.getId(), entity.getName(), entity.getDescription(), entity.getModelType(),
                entity.getDataSourceId(), entity.getSchemaName(), entity.getTableName(), entity.getDefinitionSql(),
                entity.getCollectionId(), entity.getOwnerId(), entity.getUpdatedAt(), service.fields(entity.getId()));
    }

    /**
     * 模型保存请求。
     *
     * @param name           名称
     * @param description    描述
     * @param modelType      模型类型 TABLE 或 SQL
     * @param dataSourceId   数据源标识
     * @param schemaName     表模型模式名
     * @param tableName      表模型表名
     * @param definitionSql  SQL 模型定义语句
     * @param collectionId   所属集合标识
     * @param fields         字段定义列表
     */
    public record SaveRequest(@NotBlank String name, String description, String modelType,
                              @NotNull Long dataSourceId, String schemaName, String tableName,
                              String definitionSql, Long collectionId,
                              @NotEmpty List<DatasetService.FieldInput> fields) {
    }

    /**
     * SQL 字段推断请求。
     *
     * @param dataSourceId 数据源标识
     * @param sql          模型定义 SQL
     */
    public record InferSqlFieldsRequest(@NotNull Long dataSourceId, @NotBlank String sql) {
    }

    /**
     * 模型详情视图。
     *
     * @param id             模型标识
     * @param name           名称
     * @param description    描述
     * @param modelType      模型类型
     * @param dataSourceId   数据源标识
     * @param schemaName     模式名
     * @param tableName      表名
     * @param definitionSql  SQL 定义
     * @param collectionId   所属集合标识
     * @param ownerId        所有者用户标识
     * @param updatedAt      更新时间
     * @param fields         字段定义列表
     */
    public record View(@JsonSerialize(using = ToStringSerializer.class) long id,
                       String name, String description, String modelType,
                       @JsonSerialize(using = ToStringSerializer.class) long dataSourceId,
                       String schemaName, String tableName, String definitionSql,
                       @JsonSerialize(using = ToStringSerializer.class) Long collectionId,
                       @JsonSerialize(using = ToStringSerializer.class) long ownerId,
                       LocalDateTime updatedAt,
                       List<DatasetFieldEntity> fields) {
    }
}
