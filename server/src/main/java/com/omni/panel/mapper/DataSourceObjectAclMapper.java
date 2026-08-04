package com.omni.panel.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/**
 * 数据源表/列角色拒绝规则持久化，映射 {@code bi_role_table_deny}、{@code bi_role_column_deny}。
 */
public interface DataSourceObjectAclMapper {
    @Select("""
            SELECT schema_name AS schemaName, table_name AS tableName
            FROM bi_role_table_deny
            WHERE data_source_id = #{sourceId} AND role_id = #{roleId}
            ORDER BY schema_name, table_name
            """)
    List<TableRef> listTablesForRole(long sourceId, long roleId);

    @Select("""
            SELECT schema_name AS schemaName, table_name AS tableName, column_name AS columnName
            FROM bi_role_column_deny
            WHERE data_source_id = #{sourceId} AND role_id = #{roleId}
            ORDER BY schema_name, table_name, column_name
            """)
    List<ColumnRef> listColumnsForRole(long sourceId, long roleId);

    @Select("""
            SELECT DISTINCT d.schema_name AS schemaName, d.table_name AS tableName
            FROM bi_role_table_deny d
            JOIN sys_role r ON r.id = d.role_id AND r.enabled = TRUE
            JOIN sys_user_role ur ON ur.role_id = r.id AND ur.user_id = #{userId}
            WHERE d.data_source_id = #{sourceId}
            """)
    List<TableRef> listTablesForUser(long sourceId, long userId);

    @Select("""
            SELECT DISTINCT d.schema_name AS schemaName, d.table_name AS tableName, d.column_name AS columnName
            FROM bi_role_column_deny d
            JOIN sys_role r ON r.id = d.role_id AND r.enabled = TRUE
            JOIN sys_user_role ur ON ur.role_id = r.id AND ur.user_id = #{userId}
            WHERE d.data_source_id = #{sourceId}
            """)
    List<ColumnRef> listColumnsForUser(long sourceId, long userId);

    @Select("""
            SELECT COUNT(*) FROM bi_role_table_deny d
            JOIN sys_role r ON r.id = d.role_id AND r.enabled = TRUE
            JOIN sys_user_role ur ON ur.role_id = r.id AND ur.user_id = #{userId}
            WHERE d.data_source_id = #{sourceId}
            """)
    int countTableDeniesForUser(long sourceId, long userId);

    @Select("""
            SELECT COUNT(*) FROM bi_role_column_deny d
            JOIN sys_role r ON r.id = d.role_id AND r.enabled = TRUE
            JOIN sys_user_role ur ON ur.role_id = r.id AND ur.user_id = #{userId}
            WHERE d.data_source_id = #{sourceId}
            """)
    int countColumnDeniesForUser(long sourceId, long userId);

    @Delete("""
            DELETE FROM bi_role_table_deny
            WHERE data_source_id = #{sourceId} AND role_id = #{roleId}
            """)
    int deleteTablesForRole(long sourceId, long roleId);

    @Delete("""
            DELETE FROM bi_role_column_deny
            WHERE data_source_id = #{sourceId} AND role_id = #{roleId}
            """)
    int deleteColumnsForRole(long sourceId, long roleId);

    @Insert("""
            <script>
            INSERT INTO bi_role_table_deny(role_id, data_source_id, schema_name, table_name) VALUES
            <foreach collection="tables" item="item" separator=",">
                (#{roleId}, #{sourceId}, #{item.schemaName}, #{item.tableName})
            </foreach>
            </script>
            """)
    int insertTables(long sourceId, long roleId, List<TableRef> tables);

    @Insert("""
            <script>
            INSERT INTO bi_role_column_deny(role_id, data_source_id, schema_name, table_name, column_name) VALUES
            <foreach collection="columns" item="item" separator=",">
                (#{roleId}, #{sourceId}, #{item.schemaName}, #{item.tableName}, #{item.columnName})
            </foreach>
            </script>
            """)
    int insertColumns(long sourceId, long roleId, List<ColumnRef> columns);

    record TableRef(String schemaName, String tableName) {
    }

    record ColumnRef(String schemaName, String tableName, String columnName) {
    }
}
