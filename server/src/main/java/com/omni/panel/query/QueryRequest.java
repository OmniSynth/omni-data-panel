package com.omni.panel.query;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 面向数据集元数据的语义查询请求。
 *
 * @param datasetId  数据集标识
 * @param dimensions 参与选择和分组的维度字段
 * @param metrics    参与聚合的模型指标字段名
 * @param metricIds  参与聚合的业务指标（bi_metric）标识
 * @param filter     用户过滤条件
 * @param sorts      结果排序项
 * @param limit      最大返回行数
 */
public record QueryRequest(
        @NotNull Long datasetId,
        List<String> dimensions,
        List<String> metrics,
        List<Long> metricIds,
        FilterNode filter,
        List<SortItem> sorts,
        @Min(1) @Max(50000) Integer limit
) {
    /**
     * 过滤条件节点；包含子节点时表示逻辑组合，否则表示字段条件。
     *
     * @param logic    子节点之间的逻辑关系
     * @param field    叶子条件使用的语义字段名
     * @param operator 叶子条件使用的过滤运算符
     * @param value    叶子条件的参数值
     * @param children 组合条件的子节点
     */
    public record FilterNode(String logic, String field, String operator, Object value,
                             List<FilterNode> children) {
    }

    /**
     * 结果排序项。
     *
     * @param field     已选择的语义字段名
     * @param direction 排序方向
     */
    public record SortItem(String field, String direction) {
    }
}
