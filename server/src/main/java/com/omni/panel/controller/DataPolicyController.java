package com.omni.panel.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import com.omni.panel.entity.RowRuleEntity;
import com.omni.panel.mapper.DataPolicyMapper;
import com.omni.panel.service.DataPolicyService;

/**
 * 提供模型字段权限和行级过滤规则的维护接口。
 *
 * <p>鉴权以模型 WRITE（所有者或管理员）为准，不要求进入管理端。</p>
 */
@RestController
@RequestMapping("/api/datasets/{datasetId}/policies")
@PreAuthorize("isAuthenticated()")
public class DataPolicyController {
    private final DataPolicyService service;

    /**
     * 注入数据策略业务服务。
     *
     * @param service 数据策略服务
     */
    public DataPolicyController(DataPolicyService service) {
        this.service = service;
    }

    /**
     * 列出用户在模型上的字段权限。
     *
     * @param datasetId 模型标识
     * @param userId    目标用户
     * @return 字段权限行
     */
    @GetMapping("/fields")
    public ApiResponse<List<DataPolicyMapper.FieldPermissionRow>> listFields(
            @PathVariable long datasetId, @RequestParam long userId) {
        return ApiResponse.ok(service.listFields(datasetId, userId));
    }

    /**
     * 以白名单方式整体替换用户字段权限。
     *
     * @param datasetId 模型标识
     * @param request   用户与允许字段列表
     * @return 空成功响应
     */
    @PutMapping("/fields")
    public ApiResponse<Void> replaceFields(@PathVariable long datasetId,
                                           @Valid @RequestBody ReplaceFieldsRequest request) {
        service.replaceFields(datasetId, request.userId(), request.allowedFields());
        return ApiResponse.ok();
    }

    /**
     * 列出模型上的行级规则。
     *
     * @param datasetId 模型标识
     * @return 行级规则列表
     */
    @GetMapping("/rows")
    public ApiResponse<List<RowRuleEntity>> listRows(@PathVariable long datasetId) {
        return ApiResponse.ok(service.listRows(datasetId));
    }

    /**
     * 创建全局或用户专属的行级过滤规则。
     *
     * @param datasetId 模型标识
     * @param request   规则内容
     * @return 新建规则（含标识）
     */
    @PostMapping("/rows")
    public ApiResponse<RowRuleEntity> createRow(@PathVariable long datasetId,
                                                @Valid @RequestBody RowRuleRequest request) {
        return ApiResponse.ok(service.createRow(
                datasetId, request.userId(), request.name(), request.ruleJson(),
                request.enabled() == null || request.enabled()));
    }

    /**
     * 更新行级过滤规则。
     *
     * @param datasetId 模型标识
     * @param ruleId    规则标识
     * @param request   规则内容
     * @return 更新后的规则
     */
    @PutMapping("/rows/{ruleId}")
    public ApiResponse<RowRuleEntity> updateRow(@PathVariable long datasetId, @PathVariable long ruleId,
                                                @Valid @RequestBody RowRuleRequest request) {
        return ApiResponse.ok(service.updateRow(
                datasetId, ruleId, request.userId(), request.name(), request.ruleJson(),
                request.enabled() == null || request.enabled()));
    }

    /**
     * 删除指定模型内的行级过滤规则。
     *
     * @param datasetId 模型标识
     * @param ruleId    行级规则标识
     * @return 空成功响应
     */
    @DeleteMapping("/rows/{ruleId}")
    public ApiResponse<Void> deleteRow(@PathVariable long datasetId, @PathVariable long ruleId) {
        service.deleteRow(datasetId, ruleId);
        return ApiResponse.ok();
    }

    /**
     * 批量替换字段白名单请求。
     *
     * @param userId        用户标识
     * @param allowedFields 允许访问的语义字段名；空列表表示清除字段限制
     */
    public record ReplaceFieldsRequest(@NotNull Long userId, List<String> allowedFields) {
    }

    /**
     * 行级过滤规则配置。
     *
     * @param userId   规则所属用户；为空表示全局规则
     * @param name     规则名称
     * @param ruleJson 序列化的过滤节点 JSON
     * @param enabled  是否启用；缺省为启用
     */
    public record RowRuleRequest(Long userId, @NotBlank String name, @NotBlank String ruleJson, Boolean enabled) {
    }
}
