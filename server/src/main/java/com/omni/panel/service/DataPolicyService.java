package com.omni.panel.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.entity.DatasetEntity;
import com.omni.panel.entity.DatasetFieldEntity;
import com.omni.panel.entity.RowRuleEntity;
import com.omni.panel.mapper.DataPolicyMapper;
import com.omni.panel.query.QueryRequest;

/**
 * 维护模型字段白名单与行级过滤规则；写操作要求模型 WRITE。
 */
@Service
public class DataPolicyService {
    private final DatasetService datasetService;
    private final DataPolicyMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 注入数据策略维护所需依赖。
     *
     * @param datasetService 模型服务
     * @param mapper           策略持久化
     * @param objectMapper     JSON 解析
     */
    public DataPolicyService(DatasetService datasetService, DataPolicyMapper mapper, ObjectMapper objectMapper) {
        this.datasetService = datasetService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 列出指定用户在模型上的字段权限配置。
     *
     * @param datasetId 模型标识
     * @param userId    目标用户
     * @return 字段权限行
     */
    public List<DataPolicyMapper.FieldPermissionRow> listFields(long datasetId, long userId) {
        requireWritable(datasetId);
        return mapper.listFieldPermissions(datasetId, userId);
    }

    /**
     * 以白名单方式整体替换用户字段权限：清空后仅写入允许的字段。
     *
     * <p>当 {@code allowedFields} 为空时表示清除字段限制（查询侧不再限制字段）。</p>
     *
     * @param datasetId     模型标识
     * @param userId        目标用户
     * @param allowedFields 允许访问的语义字段名
     */
    @Transactional
    public void replaceFields(long datasetId, long userId, List<String> allowedFields) {
        DatasetEntity dataset = requireWritable(datasetId);
        Set<String> known = new LinkedHashSet<>();
        for (DatasetFieldEntity field : datasetService.fields(dataset.getId())) {
            known.add(field.getName());
        }
        Set<String> next = new LinkedHashSet<>();
        if (allowedFields != null) {
            for (String name : allowedFields) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                String trimmed = name.trim();
                if (!known.contains(trimmed)) {
                    throw new BusinessException("字段不存在于模型：" + trimmed);
                }
                next.add(trimmed);
            }
        }
        mapper.deleteAllFieldPermissions(datasetId, userId);
        for (String fieldName : next) {
            mapper.saveFieldPermission(datasetId, userId, fieldName, true);
        }
    }

    /**
     * 列出模型上的全部行级规则。
     *
     * @param datasetId 模型标识
     * @return 行级规则
     */
    public List<RowRuleEntity> listRows(long datasetId) {
        requireWritable(datasetId);
        return mapper.listRowRules(datasetId);
    }

    /**
     * 创建行级规则并返回含主键的实体。
     *
     * @param datasetId 模型标识
     * @param userId    归属用户；为空表示全体用户
     * @param name      规则名称
     * @param ruleJson  过滤节点 JSON
     * @param enabled   是否启用
     * @return 新建规则
     */
    @Transactional
    public RowRuleEntity createRow(long datasetId, Long userId, String name, String ruleJson, boolean enabled) {
        requireWritable(datasetId);
        validateRuleJson(ruleJson);
        RowRuleEntity rule = new RowRuleEntity();
        rule.setDatasetId(datasetId);
        rule.setUserId(userId);
        rule.setName(name);
        rule.setRuleJson(ruleJson);
        rule.setEnabled(enabled);
        mapper.insertRowRule(rule);
        return rule;
    }

    /**
     * 更新行级规则。
     *
     * @param datasetId 模型标识
     * @param ruleId    规则标识
     * @param userId    归属用户；为空表示全体用户
     * @param name      规则名称
     * @param ruleJson  过滤节点 JSON
     * @param enabled   是否启用
     * @return 更新后的规则
     */
    @Transactional
    public RowRuleEntity updateRow(long datasetId, long ruleId, Long userId, String name, String ruleJson,
                                   boolean enabled) {
        requireWritable(datasetId);
        RowRuleEntity existing = mapper.findRowRule(ruleId, datasetId);
        if (existing == null) {
            throw new BusinessException(404, "行级规则不存在");
        }
        validateRuleJson(ruleJson);
        existing.setUserId(userId);
        existing.setName(name);
        existing.setRuleJson(ruleJson);
        existing.setEnabled(enabled);
        mapper.updateRowRule(existing);
        return existing;
    }

    /**
     * 删除行级规则。
     *
     * @param datasetId 模型标识
     * @param ruleId    规则标识
     */
    @Transactional
    public void deleteRow(long datasetId, long ruleId) {
        requireWritable(datasetId);
        if (mapper.deleteRowRule(ruleId, datasetId) == 0) {
            throw new BusinessException(404, "行级规则不存在");
        }
    }

    /**
     * 要求当前用户对模型具备 WRITE 权限。
     *
     * @param datasetId 模型标识
     * @return 模型实体
     */
    private DatasetEntity requireWritable(long datasetId) {
        return datasetService.require(datasetId, "WRITE");
    }

    /**
     * 校验行级规则 JSON 可解析为过滤节点。
     *
     * @param ruleJson 规则 JSON
     * @throws BusinessException JSON 不合法时
     */
    private void validateRuleJson(String ruleJson) {
        try {
            objectMapper.readValue(ruleJson, QueryRequest.FilterNode.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("行级规则 JSON 不合法");
        }
    }
}
