package com.omni.panel.permission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.BusinessException;
import com.omni.panel.dataset.DatasetService;
import com.omni.panel.query.QueryRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 提供数据集字段权限和行级过滤规则的维护接口。
 */
@RestController
@RequestMapping("/api/datasets/{datasetId}/policies")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('dataset:manage')")
public class DataPolicyController {
    private final DatasetService datasetService;
    private final DataPolicyMapper mapper;
    private final ObjectMapper objectMapper;

    public DataPolicyController(DatasetService datasetService, DataPolicyMapper mapper, ObjectMapper objectMapper) {
        this.datasetService = datasetService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存用户对单个字段的允许或拒绝配置。
     *
     * @param datasetId 数据集标识
     * @param request 用户、字段和允许状态
     * @return 空成功响应
     */
    @PostMapping("/fields")
    public ApiResponse<Void> saveField(@PathVariable long datasetId,
                                       @Valid @RequestBody FieldPermission request) {
        datasetService.require(datasetId, "WRITE");
        mapper.saveFieldPermission(datasetId, request.userId(), request.fieldName(), request.allowed());
        return ApiResponse.ok();
    }

    /**
     * 删除用户对单个字段的显式权限配置。
     *
     * @param datasetId 数据集标识
     * @param userId 用户标识
     * @param fieldName 语义字段名
     * @return 空成功响应
     */
    @DeleteMapping("/fields/{userId}/{fieldName}")
    public ApiResponse<Void> deleteField(@PathVariable long datasetId, @PathVariable long userId,
                                         @PathVariable String fieldName) {
        datasetService.require(datasetId, "WRITE");
        mapper.deleteFieldPermission(datasetId, userId, fieldName);
        return ApiResponse.ok();
    }

    /**
     * 创建全局或用户专属的行级过滤规则。
     *
     * @param datasetId 数据集标识
     * @param request 规则归属、名称及过滤节点 JSON
     * @return 空成功响应
     */
    @PostMapping("/rows")
    public ApiResponse<Void> createRow(@PathVariable long datasetId, @Valid @RequestBody RowRule request) {
        datasetService.require(datasetId, "WRITE");
        try {
            objectMapper.readValue(request.ruleJson(), QueryRequest.FilterNode.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("行级规则 JSON 不合法");
        }
        mapper.insertRowRule(datasetId, request.userId(), request.name(), request.ruleJson());
        return ApiResponse.ok();
    }

    /**
     * 删除指定数据集内的行级过滤规则。
     *
     * @param datasetId 数据集标识
     * @param ruleId 行级规则标识
     * @return 空成功响应
     */
    @DeleteMapping("/rows/{ruleId}")
    public ApiResponse<Void> deleteRow(@PathVariable long datasetId, @PathVariable long ruleId) {
        datasetService.require(datasetId, "WRITE");
        mapper.deleteRowRule(ruleId, datasetId);
        return ApiResponse.ok();
    }

    /**
     * 字段权限配置。
     *
     * <p>当用户在数据集上存在任意字段配置时，查询侧采用白名单语义：
     * 只有显式配置为允许的字段可用，其余字段默认拒绝。</p>
     *
     * @param userId 用户标识
     * @param fieldName 语义字段名
     * @param allowed 是否允许访问
     */
    public record FieldPermission(@NotNull Long userId, @NotBlank String fieldName, boolean allowed) {}

    /**
     * 行级过滤规则配置。
     *
     * @param userId 规则所属用户；为空表示全局规则
     * @param name 规则名称
     * @param ruleJson 序列化的过滤节点 JSON
     */
    public record RowRule(Long userId, @NotBlank String name, @NotBlank String ruleJson) {}
}
