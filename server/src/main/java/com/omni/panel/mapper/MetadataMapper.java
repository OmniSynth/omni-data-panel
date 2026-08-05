package com.omni.panel.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/**
 * 数据源元数据快照的持久化访问接口，映射 {@code bi_meta_schema}、{@code bi_meta_table}、
 * {@code bi_meta_column} 表。
 *
 * <p>由 {@link MetadataService} 同步并读取数据源结构快照，供数据集建模与 SQL 补全使用。</p>
 */
public interface MetadataMapper {
    /**
     * 删除数据源的全部字段元数据。
     *
     * @param sourceId 数据源标识
     * @return 删除行数
     */
    @Delete("DELETE FROM bi_meta_column WHERE data_source_id = #{sourceId}")
    int deleteColumns(long sourceId);

    /**
     * 删除数据源的全部表元数据。
     *
     * @param sourceId 数据源标识
     * @return 删除行数
     */
    @Delete("DELETE FROM bi_meta_table WHERE data_source_id = #{sourceId}")
    int deleteTables(long sourceId);

    /**
     * 删除数据源的全部模式元数据。
     *
     * @param sourceId 数据源标识
     * @return 删除行数
     */
    @Delete("DELETE FROM bi_meta_schema WHERE data_source_id = #{sourceId}")
    int deleteSchemas(long sourceId);

    /**
     * 保存模式元数据。
     *
     * @param sourceId   数据源标识
     * @param schemaName 模式名称
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO bi_meta_schema(data_source_id, schema_name)
            VALUES(#{sourceId}, #{schemaName})
            """)
    int insertSchema(long sourceId, String schemaName);

    /**
     * 保存表或视图元数据。
     *
     * @param sourceId   数据源标识
     * @param schemaName 模式名称
     * @param tableName  表或视图名称
     * @param comment    表注释
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO bi_meta_table(data_source_id, schema_name, table_name, table_comment)
            VALUES(#{sourceId}, #{schemaName}, #{tableName}, #{comment})
            """)
    int insertTable(long sourceId, String schemaName, String tableName, String comment);

    /**
     * 保存字段元数据，包含长度、主键与外键信息。
     *
     * @param sourceId      数据源标识
     * @param schemaName    模式名称
     * @param tableName     表名称
     * @param columnName    字段名称
     * @param dataType      JDBC 数据类型
     * @param typeName      数据库类型名称
     * @param columnSize    字段长度
     * @param decimalDigits 小数位数
     * @param nullable      是否允许空值
     * @param primaryKey    是否主键
     * @param foreignKey    是否外键
     * @param fkTableName   外键引用表
     * @param fkColumnName  外键引用字段
     * @param position      字段顺序
     * @param comment       字段注释
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO bi_meta_column(data_source_id, schema_name, table_name, column_name,
                                       data_type, type_name, column_size, decimal_digits, nullable,
                                       primary_key, foreign_key, fk_table_name, fk_column_name,
                                       ordinal_position, column_comment)
            VALUES(#{sourceId}, #{schemaName}, #{tableName}, #{columnName},
                   #{dataType}, #{typeName}, #{columnSize}, #{decimalDigits}, #{nullable},
                   #{primaryKey}, #{foreignKey}, #{fkTableName}, #{fkColumnName},
                   #{position}, #{comment})
            """)
    int insertColumn(long sourceId, String schemaName, String tableName, String columnName,
                     int dataType, String typeName, Integer columnSize, Integer decimalDigits,
                     boolean nullable, boolean primaryKey, boolean foreignKey,
                     String fkTableName, String fkColumnName, int position, String comment);

    /**
     * 查询数据源的模式名称。
     *
     * @param sourceId 数据源标识
     * @return 按名称排序的模式列表
     */
    @Select("""
            SELECT schema_name FROM bi_meta_schema
            WHERE data_source_id = #{sourceId} ORDER BY schema_name
            """)
    List<String> schemas(long sourceId);

    /**
     * 查询指定模式下的表与视图。
     *
     * @param sourceId   数据源标识
     * @param schemaName 模式名称
     * @return 按名称排序的表视图列表
     */
    @Select("""
            SELECT table_name AS tableName, table_comment AS comment
            FROM bi_meta_table WHERE data_source_id = #{sourceId} AND schema_name = #{schemaName}
            ORDER BY table_name
            """)
    List<TableView> tables(long sourceId, String schemaName);

    /**
     * 查询指定表的字段。
     *
     * @param sourceId   数据源标识
     * @param schemaName 模式名称
     * @param tableName  表名称
     * @return 按字段顺序排列的字段视图列表
     */
    @Select("""
            SELECT column_name AS columnName, type_name AS typeName, column_size AS columnSize,
                   decimal_digits AS decimalDigits, nullable, primary_key AS primaryKey,
                   foreign_key AS foreignKey, fk_table_name AS fkTableName, fk_column_name AS fkColumnName,
                   ordinal_position AS position, column_comment AS comment
            FROM bi_meta_column
            WHERE data_source_id = #{sourceId} AND schema_name = #{schemaName} AND table_name = #{tableName}
            ORDER BY ordinal_position
            """)
    List<ColumnView> columns(long sourceId, String schemaName, String tableName);

    /**
     * 判断指定字段是否存在于元数据快照。
     *
     * @param sourceId   数据源标识
     * @param schemaName 模式名称
     * @param tableName  表名称
     * @param columnName 字段名称
     * @return 匹配数量
     */
    @Select("""
            SELECT COUNT(*) FROM bi_meta_column
            WHERE data_source_id = #{sourceId} AND schema_name = #{schemaName}
              AND table_name = #{tableName} AND column_name = #{columnName}
            """)
    int columnExists(long sourceId, String schemaName, String tableName, String columnName);

    /**
     * 判断指定表是否存在于元数据快照。
     *
     * @param sourceId   数据源标识
     * @param schemaName 模式名称
     * @param tableName  表名称
     * @return 匹配数量
     */
    @Select("""
            SELECT COUNT(*) FROM bi_meta_table
            WHERE data_source_id = #{sourceId} AND schema_name = #{schemaName} AND table_name = #{tableName}
            """)
    int tableExists(long sourceId, String schemaName, String tableName);

    /**
     * 查询数据源下全部字段坐标，供 SQL 编辑器补全使用。
     *
     * @param sourceId 数据源标识
     * @return 按模式、表、字段顺序排列的补全字段列表
     */
    @Select("""
            SELECT schema_name AS schemaName, table_name AS tableName, column_name AS columnName
            FROM bi_meta_column
            WHERE data_source_id = #{sourceId}
            ORDER BY schema_name, table_name, ordinal_position
            """)
    List<CompletionColumn> completionColumns(long sourceId);

    /**
     * 表元数据查询视图。
     *
     * @param tableName 表名称
     * @param comment   表注释
     */
    record TableView(String tableName, String comment) {
    }

    /**
     * 字段元数据查询视图。
     *
     * @param columnName    字段名称
     * @param typeName      数据库类型名称
     * @param columnSize    字段长度
     * @param decimalDigits 小数位数
     * @param nullable      是否允许空值
     * @param primaryKey    是否主键
     * @param foreignKey    是否外键
     * @param fkTableName   外键引用表
     * @param fkColumnName  外键引用字段
     * @param position      字段顺序
     * @param comment       字段注释
     */
    record ColumnView(String columnName, String typeName, Integer columnSize, Integer decimalDigits,
                      boolean nullable, boolean primaryKey, boolean foreignKey,
                      String fkTableName, String fkColumnName, int position, String comment) {
    }

    /**
     * SQL 补全所需的字段坐标。
     *
     * @param schemaName 模式名称
     * @param tableName  表名称
     * @param columnName 字段名称
     */
    record CompletionColumn(String schemaName, String tableName, String columnName) {
    }
}
