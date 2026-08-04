package com.omni.panel.dataset;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.recent.RecentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提供模型及其字段定义的查询和维护接口。
 */
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {
    private final DatasetService service;
    private final RecentService recentService;

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
        recentService.touch(com.omni.panel.auth.AuthenticatedUser.current().id(), "MODEL", id);
        return ApiResponse.ok(view(entity));
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
     * @param id 模型标识
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

    private DatasetService.SaveInput toInput(SaveRequest request) {
        return new DatasetService.SaveInput(request.name(), request.description(), request.modelType(),
            request.dataSourceId(), request.schemaName(), request.tableName(), request.definitionSql(),
            request.collectionId(), request.fields());
    }

    private View view(DatasetEntity entity) {
        return new View(entity.getId(), entity.getName(), entity.getDescription(), entity.getModelType(),
            entity.getDataSourceId(), entity.getSchemaName(), entity.getTableName(), entity.getDefinitionSql(),
            entity.getCollectionId(), entity.getOwnerId(), entity.getUpdatedAt(), service.fields(entity.getId()));
    }

    /**
     * 模型保存请求。
     */
    public record SaveRequest(@NotBlank String name, String description, String modelType,
                              @NotNull Long dataSourceId, String schemaName, String tableName,
                              String definitionSql, Long collectionId,
                              @NotEmpty List<DatasetService.FieldInput> fields) {}

    /**
     * 模型详情视图。
     */
    public record View(@JsonSerialize(using = ToStringSerializer.class) long id,
                       String name, String description, String modelType,
                       @JsonSerialize(using = ToStringSerializer.class) long dataSourceId,
                       String schemaName, String tableName, String definitionSql,
                       @JsonSerialize(using = ToStringSerializer.class) Long collectionId,
                       @JsonSerialize(using = ToStringSerializer.class) long ownerId,
                       LocalDateTime updatedAt,
                       List<DatasetFieldEntity> fields) {}
}
