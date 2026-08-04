package com.omni.panel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.panel.query.JdbcQueryExecutor;

/**
 * 将查询结果压缩为审计预览 JSON。
 */
public final class QueryAuditPreview {
    private static final int MAX_ROWS = 20;
    private static final int MAX_CELL_CHARS = 200;
    private static final int MAX_JSON_CHARS = 64_000;

    /**
     * 工具类私有构造，禁止实例化。
     */
    private QueryAuditPreview() {
    }

    /**
     * @param objectMapper JSON 序列化器
     * @param result       查询结果，可为 null
     * @return 预览 JSON；无数据时返回 null
     */
    public static String build(ObjectMapper objectMapper, JdbcQueryExecutor.QueryResult result) {
        if (result == null || result.rows() == null || result.rows().isEmpty()) {
            return null;
        }
        List<String> columns = result.columns() == null ? List.of() : result.columns();
        List<Map<String, Object>> previewRows = new ArrayList<>();
        int limit = Math.min(MAX_ROWS, result.rows().size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> source = result.rows().get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            if (columns.isEmpty()) {
                source.forEach((key, value) -> row.put(key, shorten(value)));
            } else {
                for (String column : columns) {
                    row.put(column, shorten(source.get(column)));
                }
            }
            previewRows.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("columns", columns);
        payload.put("rows", previewRows);
        payload.put("previewRowCount", previewRows.size());
        payload.put("totalRowCount", result.rows().size());
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (json.length() <= MAX_JSON_CHARS) {
                return json;
            }
            return json.substring(0, MAX_JSON_CHARS);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    /**
     * 截断过长的单元格值为审计预览可接受的长度。
     *
     * @param value 原始单元格值
     * @return 缩短后的值；数值和布尔值原样返回
     */
    private static Object shorten(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = String.valueOf(value);
        if (text.length() <= MAX_CELL_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CELL_CHARS) + "…";
    }
}
