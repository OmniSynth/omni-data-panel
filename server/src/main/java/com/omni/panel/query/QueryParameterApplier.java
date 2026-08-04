package com.omni.panel.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.omni.panel.common.BusinessException;
import com.omni.panel.service.QueryService;

/**
 * 将仪表盘参数值按卡片绑定合并进查询提交。
 */
@Component
public class QueryParameterApplier {
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper JSON 解析器
     */
    public QueryParameterApplier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 参数绑定定义。
     *
     * @param parameterId    仪表盘参数标识
     * @param mode           semantic 或 sql
     * @param field          语义字段名
     * @param operator       语义运算符
     * @param parameterIndex SQL 占位参数下标
     */
    public record Binding(String parameterId, String mode, String field, String operator, Integer parameterIndex) {
    }

    /**
     * 仪表盘参数元数据（用于 date-range / required 判断）。
     *
     * @param id       参数标识
     * @param type     参数类型
     * @param required 是否必填
     */
    public record ParameterMeta(String id, String type, boolean required) {
    }

    /**
     * 应用绑定后返回新的查询提交。
     *
     * @param submission 原始查询
     * @param bindings   卡片绑定
     * @param values     运行时参数值
     * @param metas      参数元数据
     * @return 合并后的查询
     */
    public QueryService.QuerySubmission apply(QueryService.QuerySubmission submission,
                                              List<Binding> bindings,
                                              Map<String, Object> values,
                                              Map<String, ParameterMeta> metas) {
        if (submission == null || bindings == null || bindings.isEmpty()) {
            return submission;
        }
        Map<String, Object> safeValues = values == null ? Map.of() : values;
        Map<String, ParameterMeta> safeMetas = metas == null ? Map.of() : metas;
        if (submission.query() != null) {
            return applySemantic(submission, bindings, safeValues, safeMetas);
        }
        return applySql(submission, bindings, safeValues, safeMetas);
    }

    /**
     * 解析 bindings JSON。
     *
     * @param bindingsJson 绑定 JSON
     * @return 绑定列表
     */
    public List<Binding> parseBindings(String bindingsJson) {
        if (bindingsJson == null || bindingsJson.isBlank()) {
            return List.of();
        }
        try {
            List<Binding> list = objectMapper.readValue(bindingsJson, new TypeReference<>() {
            });
            return list == null ? List.of() : List.copyOf(list);
        } catch (Exception exception) {
            throw new BusinessException("卡片参数绑定配置无效");
        }
    }

    /**
     * 从仪表盘 configJson 解析参数元数据。
     *
     * @param configJson 仪表盘配置
     * @return 参数元数据映射
     */
    public Map<String, ParameterMeta> parseParameterMetas(String configJson) {
        Map<String, ParameterMeta> result = new LinkedHashMap<>();
        if (configJson == null || configJson.isBlank()) {
            return result;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(configJson, new TypeReference<>() {
            });
            Object raw = root.get("parameters");
            if (!(raw instanceof List<?> list)) {
                return result;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object id = map.get("id");
                if (id == null || String.valueOf(id).isBlank()) {
                    continue;
                }
                String key = String.valueOf(id);
                String type = map.get("type") == null ? "text" : String.valueOf(map.get("type"));
                boolean required = Boolean.TRUE.equals(map.get("required"))
                        || "true".equalsIgnoreCase(String.valueOf(map.get("required")));
                result.put(key, new ParameterMeta(key, type, required));
            }
            return result;
        } catch (Exception exception) {
            return result;
        }
    }

    /**
     * 提取仪表盘参数默认值。
     *
     * @param configJson 仪表盘配置
     * @return 默认值映射
     */
    public Map<String, Object> defaultParameterValues(String configJson) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (configJson == null || configJson.isBlank()) {
            return result;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(configJson, new TypeReference<>() {
            });
            Object raw = root.get("parameters");
            if (!(raw instanceof List<?> list)) {
                return result;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map) || map.get("id") == null) {
                    continue;
                }
                if (map.containsKey("defaultValue")) {
                    result.put(String.valueOf(map.get("id")), map.get("defaultValue"));
                }
            }
            return result;
        } catch (Exception exception) {
            return result;
        }
    }

    /**
     * 将 semantic 绑定合并进语义查询的过滤树。
     *
     * @param submission 原始查询
     * @param bindings   卡片绑定
     * @param values     运行时参数值
     * @param metas      参数元数据
     * @return 合并后的语义查询
     */
    private QueryService.QuerySubmission applySemantic(QueryService.QuerySubmission submission,
                                                       List<Binding> bindings,
                                                       Map<String, Object> values,
                                                       Map<String, ParameterMeta> metas) {
        QueryRequest query = submission.query();
        List<QueryRequest.FilterNode> leaves = new ArrayList<>();
        if (query.filter() != null) {
            collectLeaves(query.filter(), leaves);
        }
        for (Binding binding : bindings) {
            if (!"semantic".equalsIgnoreCase(binding.mode()) || binding.field() == null || binding.field().isBlank()) {
                continue;
            }
            ParameterMeta meta = metas.get(binding.parameterId());
            Object value = values.get(binding.parameterId());
            if (isEmptyValue(value)) {
                if (meta != null && meta.required()) {
                    throw new BusinessException("参数 " + binding.parameterId() + " 不能为空");
                }
                continue;
            }
            String type = meta == null ? "text" : meta.type();
            if ("date-range".equalsIgnoreCase(type)) {
                applyDateRange(leaves, binding.field(), value);
                continue;
            }
            String operator = resolveOperator(binding.operator(), type, value);
            Object normalized = normalizeValue(value, operator);
            upsertLeaf(leaves, binding.field(), operator, normalized);
        }
        QueryRequest.FilterNode filter = leaves.isEmpty() ? null
                : new QueryRequest.FilterNode("AND", null, null, null, List.copyOf(leaves));
        QueryRequest merged = new QueryRequest(query.datasetId(), query.dimensions(), query.metrics(),
                query.metricIds(), filter, query.sorts(), query.limit());
        return new QueryService.QuerySubmission(null, null, null, merged);
    }

    /**
     * 将 sql 绑定写入 JDBC 占位参数列表（按 parameterIndex）。
     *
     * @param submission 原始查询
     * @param bindings   卡片绑定
     * @param values     运行时参数值
     * @param metas      参数元数据
     * @return 合并后的原生 SQL 查询
     */
    private QueryService.QuerySubmission applySql(QueryService.QuerySubmission submission,
                                                  List<Binding> bindings,
                                                  Map<String, Object> values,
                                                  Map<String, ParameterMeta> metas) {
        List<Object> parameters = new ArrayList<>();
        if (submission.parameters() != null) {
            parameters.addAll(submission.parameters());
        }
        for (Binding binding : bindings) {
            if (!"sql".equalsIgnoreCase(binding.mode()) || binding.parameterIndex() == null) {
                continue;
            }
            int index = binding.parameterIndex();
            if (index < 0) {
                throw new BusinessException("SQL 参数下标无效");
            }
            ParameterMeta meta = metas.get(binding.parameterId());
            Object value = values.get(binding.parameterId());
            if (isEmptyValue(value)) {
                if (meta != null && meta.required()) {
                    throw new BusinessException("参数 " + binding.parameterId() + " 不能为空");
                }
                continue;
            }
            if (value instanceof Collection<?> || (value != null && value.getClass().isArray())) {
                throw new BusinessException("SQL 参数仅支持标量值");
            }
            if ("date-range".equalsIgnoreCase(meta == null ? "" : meta.type())) {
                throw new BusinessException("SQL 参数不支持 date-range，请拆成两个标量参数");
            }
            while (parameters.size() <= index) {
                parameters.add(null);
            }
            parameters.set(index, scalarValue(value));
        }
        return new QueryService.QuerySubmission(submission.sourceId(), submission.sql(),
                List.copyOf(parameters), null);
    }

    /**
     * 将日期区间参数展开为字段上的 GTE / LTE 叶子条件。
     *
     * @param leaves 可变叶子列表
     * @param field  语义字段名
     * @param value  Map/List/逗号分隔字符串形式的区间
     */
    private void applyDateRange(List<QueryRequest.FilterNode> leaves, String field, Object value) {
        Object start = null;
        Object end = null;
        if (value instanceof Map<?, ?> map) {
            start = map.get("start") != null ? map.get("start") : map.get("from");
            end = map.get("end") != null ? map.get("end") : map.get("to");
        } else if (value instanceof List<?> list && list.size() >= 2) {
            start = list.get(0);
            end = list.get(1);
        } else if (value instanceof String text && text.contains(",")) {
            String[] parts = text.split(",", 2);
            start = parts[0].trim();
            end = parts[1].trim();
        }
        if (!isEmptyValue(start)) {
            upsertLeaf(leaves, field, "GTE", scalarValue(start));
        }
        if (!isEmptyValue(end)) {
            upsertLeaf(leaves, field, "LTE", scalarValue(end));
        }
    }

    /**
     * 深度优先收集过滤树中的叶子条件节点。
     *
     * @param node   过滤树节点
     * @param leaves 输出叶子列表
     */
    private void collectLeaves(QueryRequest.FilterNode node, List<QueryRequest.FilterNode> leaves) {
        if (node == null) {
            return;
        }
        if (node.children() != null && !node.children().isEmpty()) {
            for (QueryRequest.FilterNode child : node.children()) {
                collectLeaves(child, leaves);
            }
            return;
        }
        if (node.field() != null && !node.field().isBlank()) {
            leaves.add(node);
        }
    }

    /**
     * 按字段与运算符更新已有叶子，否则追加新叶子。
     *
     * @param leaves   可变叶子列表
     * @param field    语义字段名
     * @param operator 运算符
     * @param value    条件值
     */
    private void upsertLeaf(List<QueryRequest.FilterNode> leaves, String field, String operator, Object value) {
        for (int i = 0; i < leaves.size(); i++) {
            QueryRequest.FilterNode existing = leaves.get(i);
            if (field.equals(existing.field())
                    && (operator.equals(existing.operator())
                    || ("GTE".equals(operator) || "LTE".equals(operator)) && operator.equals(existing.operator()))) {
                leaves.set(i, new QueryRequest.FilterNode(null, field, operator, value, null));
                return;
            }
            if (field.equals(existing.field()) && !"GTE".equals(operator) && !"LTE".equals(operator)
                    && !"GTE".equals(existing.operator()) && !"LTE".equals(existing.operator())) {
                leaves.set(i, new QueryRequest.FilterNode(null, field, operator, value, null));
                return;
            }
        }
        leaves.add(new QueryRequest.FilterNode(null, field, operator, value, null));
    }

    /**
     * 解析语义运算符：优先绑定配置，否则多选/集合用 IN，其余用 EQ。
     *
     * @param configured 绑定上配置的运算符
     * @param type       参数类型
     * @param value      运行时值
     * @return 大写运算符
     */
    private String resolveOperator(String configured, String type, Object value) {
        if (configured != null && !configured.isBlank()) {
            return configured.toUpperCase();
        }
        if ("multi-select".equalsIgnoreCase(type) || value instanceof Collection<?>) {
            return "IN";
        }
        return "EQ";
    }

    /**
     * 按运算符规范化条件值（IN 转为列表，其余取标量）。
     *
     * @param value    原始值
     * @param operator 运算符
     * @return 规范化后的值
     */
    private Object normalizeValue(Object value, String operator) {
        if ("IN".equalsIgnoreCase(operator)) {
            if (value instanceof Collection<?> collection) {
                return collection.stream().map(this::scalarValue).toList();
            }
            if (value instanceof String text && text.contains(",")) {
                List<Object> parts = new ArrayList<>();
                for (String part : text.split(",")) {
                    if (!part.isBlank()) {
                        parts.add(part.trim());
                    }
                }
                return parts;
            }
            return List.of(scalarValue(value));
        }
        return scalarValue(value);
    }

    /**
     * 将 {@code {value:...}} 包装对象解包为标量，其余原样返回。
     *
     * @param value 原始值
     * @return 标量值
     */
    private Object scalarValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("value")) {
                return map.get("value");
            }
        }
        return value;
    }

    /**
     * 判断参数值是否视为空（null、空白字符串、空集合或空 Map）。
     *
     * @param value 运行时值
     * @return 为空时返回 {@code true}
     */
    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }
}
