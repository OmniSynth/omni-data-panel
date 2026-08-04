package com.omni.panel.datasource.dialect;

/**
 * 方言元数据回退查询返回的列信息。
 */
public record DialectColumnInfo(
    String columnName,
    String typeName,
    Integer columnSize,
    Integer decimalDigits,
    boolean nullable,
    int position,
    String comment
) {}
