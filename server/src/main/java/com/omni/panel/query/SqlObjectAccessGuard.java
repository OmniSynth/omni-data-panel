package com.omni.panel.query;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;
import com.omni.panel.common.BusinessException;
import com.omni.panel.service.DataSourceObjectAclService;
import com.omni.panel.service.DataSourceObjectAclService.EffectiveDenies;

/**
 * 校验原生 SQL 是否引用了被拒绝的表/列或系统元数据库。
 */
@Component
public class SqlObjectAccessGuard {
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "mysql", "performance_schema", "sys",
            "pg_catalog", "pg_toast");

    private final DataSourceObjectAclService aclService;

    public SqlObjectAccessGuard(DataSourceObjectAclService aclService) {
        this.aclService = aclService;
    }

    /**
     * 加载当前用户 deny 后校验 SQL。
     *
     * @param sourceId      数据源标识
     * @param sql           SQL
     * @param defaultSchema 默认模式
     */
    public void validateForCurrentUser(long sourceId, String sql, String defaultSchema) {
        validate(sourceId, sql, defaultSchema, aclService.effectiveDenies(sourceId));
    }

    /**
     * 按数据源对象 ACL 校验 SQL。
     *
     * @param sourceId      数据源标识（保留便于扩展）
     * @param sql           SQL 文本
     * @param defaultSchema 默认库/模式
     * @param denies        当前用户有效拒绝集
     */
    public void validate(long sourceId, String sql, String defaultSchema, EffectiveDenies denies) {
        if (denies == null) {
            denies = EffectiveDenies.none();
        }
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException exception) {
            failClosedIfRestricted(denies, "无法解析 SQL 以校验表列权限");
            return;
        }
        if (!(statement instanceof Select select)) {
            throw new BusinessException("仅允许单条 SELECT 或 WITH SELECT");
        }

        Set<String> rawTables;
        try {
            rawTables = new HashSet<>(new TablesNamesFinder().getTables((Statement) select));
        } catch (Exception exception) {
            failClosedIfRestricted(denies, "无法解析 SQL 表引用以校验权限");
            return;
        }

        Map<String, String> aliasToTableKey = new HashMap<>();
        Set<String> referencedTableKeys = new HashSet<>();
        for (String raw : rawTables) {
            QualifiedName qn = parseQualifiedName(raw, defaultSchema);
            if (SYSTEM_SCHEMAS.contains(DataSourceObjectAclService.normalize(qn.schema()))) {
                throw new BusinessException(403, "禁止访问系统元数据库：" + qn.schema());
            }
            String key = DataSourceObjectAclService.tableKey(qn.schema(), qn.name());
            referencedTableKeys.add(key);
            if (denies.isTableDenied(qn.schema(), qn.name())) {
                throw new BusinessException(403, "无权访问表 " + display(qn));
            }
        }

        collectAliases(select.getPlainSelect(), defaultSchema, aliasToTableKey);
        validateSelectColumns(select.getPlainSelect(), defaultSchema, aliasToTableKey, referencedTableKeys, denies);
    }

    private void failClosedIfRestricted(EffectiveDenies denies, String message) {
        if (!denies.isEmpty() || denies.configured()) {
            throw new BusinessException(403, message);
        }
    }

    private void validateSelectColumns(PlainSelect plain, String defaultSchema,
                                       Map<String, String> aliasToTableKey,
                                       Set<String> referencedTableKeys,
                                       EffectiveDenies denies) {
        if (denies.isEmpty() || plain == null || plain.getSelectItems() == null) {
            if (plain == null && (!denies.isEmpty() || denies.configured())) {
                throw new BusinessException(403, "无法解析 SQL 列引用以校验权限");
            }
            return;
        }
        for (SelectItem<?> item : plain.getSelectItems()) {
            Expression expression = item.getExpression();
            if (expression instanceof AllColumns) {
                for (String tableKey : referencedTableKeys) {
                    if (hasColumnDenyForTable(denies, tableKey)) {
                        throw new BusinessException(403, "存在列级限制时不允许 SELECT *");
                    }
                }
                continue;
            }
            if (expression instanceof AllTableColumns allTable) {
                String tableKey = resolveTableKey(allTable.getTable(), defaultSchema, aliasToTableKey);
                if (tableKey != null && hasColumnDenyForTable(denies, tableKey)) {
                    throw new BusinessException(403, "存在列级限制时不允许 SELECT 表.*");
                }
                continue;
            }
            if (expression instanceof Column column) {
                String tableKey = resolveColumnTableKey(column, defaultSchema, aliasToTableKey, referencedTableKeys);
                String columnName = stripQuotes(column.getColumnName());
                if (tableKey == null || columnName == null || columnName.isBlank()) {
                    continue;
                }
                String[] parts = tableKey.split("\u0001", 2);
                String schema = parts.length > 0 ? parts[0] : "";
                String table = parts.length > 1 ? parts[1] : "";
                if (denies.isColumnDenied(schema, table, columnName)) {
                    throw new BusinessException(403, "无权访问列 " + schema + "." + table + "." + columnName);
                }
            }
        }
    }

    private boolean hasColumnDenyForTable(EffectiveDenies denies, String tableKey) {
        String prefix = tableKey + "\u0001";
        for (String columnKey : denies.columns()) {
            if (columnKey.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void collectAliases(PlainSelect plain, String defaultSchema, Map<String, String> aliasToTableKey) {
        if (plain == null) {
            return;
        }
        registerFromItem(plain.getFromItem(), defaultSchema, aliasToTableKey);
        List<Join> joins = plain.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                registerFromItem(join.getRightItem(), defaultSchema, aliasToTableKey);
            }
        }
    }

    private void registerFromItem(FromItem fromItem, String defaultSchema, Map<String, String> aliasToTableKey) {
        if (!(fromItem instanceof Table table)) {
            return;
        }
        QualifiedName qn = fromTable(table, defaultSchema);
        String key = DataSourceObjectAclService.tableKey(qn.schema(), qn.name());
        Alias alias = table.getAlias();
        if (alias != null && alias.getName() != null) {
            aliasToTableKey.put(DataSourceObjectAclService.normalize(stripQuotes(alias.getName())), key);
        }
        aliasToTableKey.put(DataSourceObjectAclService.normalize(qn.name()), key);
    }

    private String resolveTableKey(Table table, String defaultSchema, Map<String, String> aliasToTableKey) {
        if (table == null) {
            return null;
        }
        if (table.getName() != null) {
            String token = DataSourceObjectAclService.normalize(stripQuotes(table.getName()));
            if (aliasToTableKey.containsKey(token)) {
                return aliasToTableKey.get(token);
            }
        }
        QualifiedName qn = fromTable(table, defaultSchema);
        return DataSourceObjectAclService.tableKey(qn.schema(), qn.name());
    }

    private String resolveColumnTableKey(Column column, String defaultSchema,
                                         Map<String, String> aliasToTableKey,
                                         Set<String> referencedTableKeys) {
        Table table = column.getTable();
        if (table != null && table.getName() != null) {
            String token = DataSourceObjectAclService.normalize(stripQuotes(table.getName()));
            if (aliasToTableKey.containsKey(token)) {
                return aliasToTableKey.get(token);
            }
            QualifiedName qn = fromTable(table, defaultSchema);
            return DataSourceObjectAclService.tableKey(qn.schema(), qn.name());
        }
        if (referencedTableKeys.size() == 1) {
            return referencedTableKeys.iterator().next();
        }
        return null;
    }

    private QualifiedName fromTable(Table table, String defaultSchema) {
        String name = stripQuotes(table.getName());
        String schema = table.getSchemaName() != null ? stripQuotes(table.getSchemaName()) : null;
        if ((schema == null || schema.isBlank()) && name != null && name.contains(".")) {
            return parseQualifiedName(name, defaultSchema);
        }
        if (schema == null || schema.isBlank()) {
            schema = defaultSchema == null ? "" : defaultSchema;
        }
        return new QualifiedName(schema, name == null ? "" : name);
    }

    private QualifiedName parseQualifiedName(String raw, String defaultSchema) {
        String cleaned = stripQuotes(raw == null ? "" : raw.trim());
        if (cleaned.isEmpty()) {
            return new QualifiedName(defaultSchema == null ? "" : defaultSchema, "");
        }
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0) {
            return new QualifiedName(defaultSchema == null ? "" : defaultSchema, cleaned);
        }
        return new QualifiedName(cleaned.substring(0, dot), cleaned.substring(dot + 1));
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '`' && last == '`') || (first == '"' && last == '"') || (first == '[' && last == ']')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }

    private static String display(QualifiedName qn) {
        if (qn.schema() == null || qn.schema().isBlank()) {
            return qn.name();
        }
        return qn.schema() + "." + qn.name();
    }

    private record QualifiedName(String schema, String name) {
    }
}
