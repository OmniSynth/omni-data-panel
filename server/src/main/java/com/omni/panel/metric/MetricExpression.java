package com.omni.panel.metric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.panel.common.BusinessException;

/**
 * 解析指标 {@code expressionJson}，当前约定为 {@code {"field":"语义字段名"}}。
 */
public final class MetricExpression {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 工具类，禁止实例化。
     */
    private MetricExpression() {
    }

    /**
     * 从表达式 JSON 读取引用的模型字段名。
     *
     * @param expressionJson 指标表达式
     * @return 非空字段名
     */
    public static String requireFieldName(String expressionJson) {
        if (expressionJson == null || expressionJson.isBlank()) {
            throw new BusinessException("指标表达式不能为空");
        }
        try {
            JsonNode root = MAPPER.readTree(expressionJson);
            JsonNode field = root.get("field");
            if (field == null || field.isNull() || field.asText().isBlank()) {
                throw new BusinessException("指标表达式须包含 field");
            }
            return field.asText().trim();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("指标表达式解析失败");
        }
    }
}
