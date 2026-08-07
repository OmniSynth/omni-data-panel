package com.omni.panel.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.DataSourceEntity;
import com.omni.panel.mapper.DataSourceMapper;
import com.omni.panel.mapper.DataSourceObjectAclMapper;
import com.omni.panel.mapper.ResourcePermissionMapper;

/**
 * 数据源表/列角色拒绝规则（1A：无规则则不限制；多角色 deny 并集）。
 */
@Service
public class DataSourceObjectAclService {
    private final DataSourceObjectAclMapper mapper;
    private final DataSourceMapper dataSourceMapper;
    private final ResourcePermissionMapper resourcePermissionMapper;

    /**
     * 注入数据源对象 ACL 维护所需依赖。
     *
     * @param mapper                   对象 ACL 持久化
     * @param dataSourceMapper         数据源持久化
     * @param resourcePermissionMapper 角色权限查询
     */
    public DataSourceObjectAclService(DataSourceObjectAclMapper mapper, DataSourceMapper dataSourceMapper,
                                      ResourcePermissionMapper resourcePermissionMapper) {
        this.mapper = mapper;
        this.dataSourceMapper = dataSourceMapper;
        this.resourcePermissionMapper = resourcePermissionMapper;
    }

    /**
     * 计算当前用户在数据源上的有效拒绝集。
     *
     * @param sourceId 数据源标识
     * @return 有效拒绝规则；管理员与所有者为空限制
     */
    public EffectiveDenies effectiveDenies(long sourceId) {
        AuthenticatedUser user = AuthenticatedUser.current();
        DataSourceEntity source = dataSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException(404, "数据源不存在");
        }
        if (user.admin() || user.id() == source.getOwnerId()) {
            return EffectiveDenies.none();
        }
        Set<String> tables = new HashSet<>();
        for (DataSourceObjectAclMapper.TableRef ref : mapper.listTablesForUser(sourceId, user.id())) {
            tables.add(tableKey(ref.schemaName(), ref.tableName()));
        }
        Set<String> columns = new HashSet<>();
        for (DataSourceObjectAclMapper.ColumnRef ref : mapper.listColumnsForUser(sourceId, user.id())) {
            columns.add(columnKey(ref.schemaName(), ref.tableName(), ref.columnName()));
        }
        return new EffectiveDenies(tables, columns, !tables.isEmpty() || !columns.isEmpty());
    }

    /**
     * 管理端读取某角色在数据源上的拒绝配置。
     *
     * @param sourceId 数据源标识
     * @param roleId   角色标识
     * @return 配置视图
     */
    public AclView getForRole(long sourceId, long roleId) {
        requireAdminAndSource(sourceId);
        requireAssignableRole(roleId);
        return new AclView(roleId,
                List.copyOf(mapper.listTablesForRole(sourceId, roleId)),
                List.copyOf(mapper.listColumnsForRole(sourceId, roleId)));
    }

    /**
     * 整体替换某角色在数据源上的表/列拒绝规则。
     *
     * @param sourceId 数据源标识
     * @param roleId   角色标识
     * @param tables   拒绝的表
     * @param columns  拒绝的列
     */
    @Transactional
    public void replace(long sourceId, long roleId,
                        List<DataSourceObjectAclMapper.TableRef> tables,
                        List<DataSourceObjectAclMapper.ColumnRef> columns) {
        requireAdminAndSource(sourceId);
        requireAssignableRole(roleId);
        List<DataSourceObjectAclMapper.TableRef> nextTables = sanitizeTables(tables);
        List<DataSourceObjectAclMapper.ColumnRef> nextColumns = sanitizeColumns(columns);
        mapper.deleteTablesForRole(sourceId, roleId);
        mapper.deleteColumnsForRole(sourceId, roleId);
        if (!nextTables.isEmpty()) {
            mapper.insertTables(sourceId, roleId, nextTables);
        }
        if (!nextColumns.isEmpty()) {
            mapper.insertColumns(sourceId, roleId, nextColumns);
        }
    }

    /**
     * 断言当前用户可访问指定表。
     *
     * @param sourceId   数据源标识
     * @param schemaName 模式名
     * @param tableName  表名
     */
    public void requireTableAllowed(long sourceId, String schemaName, String tableName) {
        EffectiveDenies denies = effectiveDenies(sourceId);
        if (denies.isTableDenied(schemaName, tableName)) {
            throw new BusinessException(403, "无权访问表 " + schemaName + "." + tableName);
        }
    }

    /**
     * 断言当前用户可访问指定列。
     *
     * @param sourceId   数据源标识
     * @param schemaName 模式名
     * @param tableName  表名
     * @param columnName 列名
     */
    public void requireColumnAllowed(long sourceId, String schemaName, String tableName, String columnName) {
        requireTableAllowed(sourceId, schemaName, tableName);
        EffectiveDenies denies = effectiveDenies(sourceId);
        if (denies.isColumnDenied(schemaName, tableName, columnName)) {
            throw new BusinessException(403, "无权访问列 " + schemaName + "." + tableName + "." + columnName);
        }
    }

    /**
     * 生成表级拒绝键（模式与表名规范化后以分隔符连接）。
     *
     * @param schema 模式名
     * @param table  表名
     * @return 表键
     */
    public static String tableKey(String schema, String table) {
        return normalize(schema) + "\u0001" + normalize(table);
    }

    /**
     * 生成列级拒绝键（在表键基础上追加列名）。
     *
     * @param schema 模式名
     * @param table  表名
     * @param column 列名
     * @return 列键
     */
    public static String columnKey(String schema, String table, String column) {
        return tableKey(schema, table) + "\u0001" + normalize(column);
    }

    /**
     * 规范化标识符（去空白并转小写）。
     *
     * @param value 原始值
     * @return 规范化字符串；{@code null} 时返回空串
     */
    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 要求当前用户为管理员且数据源存在。
     *
     * @param sourceId 数据源标识
     * @throws BusinessException 非管理员或数据源不存在时
     */
    private void requireAdminAndSource(long sourceId) {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可配置表列权限");
        }
        if (dataSourceMapper.selectById(sourceId) == null) {
            throw new BusinessException(404, "数据源不存在");
        }
    }

    /**
     * 要求角色可配置（启用且非管理员角色）。
     *
     * @param roleId 角色标识
     * @throws BusinessException 角色不可配置时
     */
    private void requireAssignableRole(long roleId) {
        if (resourcePermissionMapper.assignableRole(roleId) == 0) {
            throw new BusinessException("只能配置启用的非管理员角色");
        }
    }

    /**
     * 去重并过滤无效的表拒绝项。
     *
     * @param tables 原始表列表
     * @return 规范化后的表列表
     */
    private List<DataSourceObjectAclMapper.TableRef> sanitizeTables(List<DataSourceObjectAclMapper.TableRef> tables) {
        Set<String> seen = new HashSet<>();
        List<DataSourceObjectAclMapper.TableRef> result = new ArrayList<>();
        if (tables == null) {
            return result;
        }
        for (DataSourceObjectAclMapper.TableRef ref : tables) {
            if (ref == null || blank(ref.schemaName()) || blank(ref.tableName())) {
                continue;
            }
            String schema = ref.schemaName().trim();
            String table = ref.tableName().trim();
            String key = tableKey(schema, table);
            if (seen.add(key)) {
                result.add(new DataSourceObjectAclMapper.TableRef(schema, table));
            }
        }
        return result;
    }

    /**
     * 去重并过滤无效的列拒绝项。
     *
     * @param columns 原始列列表
     * @return 规范化后的列列表
     */
    private List<DataSourceObjectAclMapper.ColumnRef> sanitizeColumns(List<DataSourceObjectAclMapper.ColumnRef> columns) {
        Set<String> seen = new HashSet<>();
        List<DataSourceObjectAclMapper.ColumnRef> result = new ArrayList<>();
        if (columns == null) {
            return result;
        }
        for (DataSourceObjectAclMapper.ColumnRef ref : columns) {
            if (ref == null || blank(ref.schemaName()) || blank(ref.tableName()) || blank(ref.columnName())) {
                continue;
            }
            String schema = ref.schemaName().trim();
            String table = ref.tableName().trim();
            String column = ref.columnName().trim();
            String key = columnKey(schema, table, column);
            if (seen.add(key)) {
                result.add(new DataSourceObjectAclMapper.ColumnRef(schema, table, column));
            }
        }
        return result;
    }

    /**
     * 判断字符串为空或仅空白。
     *
     * @param value 原始值
     * @return 空白时返回 {@code true}
     */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 有效拒绝集。
     *
     * @param tables     拒绝表键
     * @param columns    拒绝列键
     * @param configured 用户角色在该源上是否存在任意 deny 配置（含空集查询后仍可能 true 若 count&gt;0，通常与集合一致）
     */
    public record EffectiveDenies(Set<String> tables, Set<String> columns, boolean configured) {
        /**
         * 返回无任何拒绝规则的空实例。
         */
        public static EffectiveDenies none() {
            return new EffectiveDenies(Set.of(), Set.of(), false);
        }

        /**
         * 判断表是否在拒绝集中。
         *
         * @param schema 模式名
         * @param table  表名
         * @return 被拒绝时返回 {@code true}
         */
        public boolean isTableDenied(String schema, String table) {
            return tables.contains(tableKey(schema, table));
        }

        /**
         * 判断列是否在拒绝集中（含整表拒绝）。
         *
         * @param schema 模式名
         * @param table  表名
         * @param column 列名
         * @return 被拒绝时返回 {@code true}
         */
        public boolean isColumnDenied(String schema, String table, String column) {
            return isTableDenied(schema, table) || columns.contains(columnKey(schema, table, column));
        }

        /**
         * 拒绝集是否为空（无表且无列）。
         */
        public boolean isEmpty() {
            return tables.isEmpty() && columns.isEmpty();
        }
    }

    /**
     * 角色 ACL 视图。
     *
     * @param roleId  角色标识
     * @param tables  拒绝的表
     * @param columns 拒绝的列
     */
    public record AclView(@JsonSerialize(using = ToStringSerializer.class) long roleId,
                          List<DataSourceObjectAclMapper.TableRef> tables,
                          List<DataSourceObjectAclMapper.ColumnRef> columns) {
    }
}
