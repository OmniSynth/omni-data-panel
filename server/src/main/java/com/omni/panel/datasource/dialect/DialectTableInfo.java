package com.omni.panel.datasource.dialect;

/**
 * 方言元数据回退查询返回的表信息。
 *
 * @param name 表名
 * @param comment 注释
 */
public record DialectTableInfo(String name, String comment) {}
