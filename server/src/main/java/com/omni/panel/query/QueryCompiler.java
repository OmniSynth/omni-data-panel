package com.omni.panel.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.dialect.DialectPlugin;
import com.omni.panel.datasource.dialect.MysqlDialectPlugin;

/**
 * 将语义查询编译为只读参数化 SQL。
 *
 * <p>可参与查询的字段来自数据集元数据白名单，库名、表名、列名和别名均需通过标识符校验；
 * 过滤值只进入参数列表，不直接拼接到 SQL。用户过滤条件与行级权限规则以 {@code AND}
 * 合并，指标只能使用受支持的聚合函数，并在维度和指标混合选择时按全部维度分组。</p>
 */
@Component
public class QueryCompiler {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_$]+");
    private static final Set<String> AGGREGATIONS = Set.of("SUM", "COUNT", "AVG", "MIN", "MAX");
    private static final Set<String> OPERATORS =
            Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "LIKE", "IN", "IS_NULL", "NOT_NULL");

    /**
     * 根据数据集元数据、字段权限和行级规则编译语义查询。
     *
     * @param request 用户提交的维度、指标、过滤、排序和行数限制
     * @param dataset 数据集物理位置及字段元数据白名单
     * @param deniedFields 当前用户不可访问的字段名称
     * @param rowRules 应强制应用的行级权限过滤规则
     * @return SQL 文本及按占位符顺序排列的参数
     */
    /**
     * 兼容单测：默认使用 MySQL 方言编译。
     */
    public CompiledQuery compile(QueryRequest request, DatasetDefinition dataset,
                                 Set<String> deniedFields, List<QueryRequest.FilterNode> rowRules) {
        return compile(request, dataset, deniedFields, rowRules, new MysqlDialectPlugin());
    }

    /**
     * 根据数据集元数据、字段权限和行级规则，按指定方言编译语义查询。
     *
     * @param request      用户提交的维度、指标、过滤、排序和行数限制
     * @param dataset      数据集物理位置及字段元数据白名单
     * @param deniedFields 当前用户不可访问的字段名称
     * @param rowRules     应强制应用的行级权限过滤规则
     * @param dialect      目标数据库方言，为 null 时使用 MySQL
     * @return SQL 文本及按占位符顺序排列的参数
     */
    public CompiledQuery compile(QueryRequest request, DatasetDefinition dataset,
                                 Set<String> deniedFields, List<QueryRequest.FilterNode> rowRules,
                                 DialectPlugin dialect) {
        DialectPlugin plugin = dialect == null ? new MysqlDialectPlugin() : dialect;
        Map<String, FieldDefinition> fields = new HashMap<>();
        dataset.fields().forEach(field -> fields.put(field.name(), field));
        List<String> dimensions = safeList(request.dimensions());
        List<String> metrics = safeList(request.metrics());
        if (dimensions.isEmpty() && metrics.isEmpty()) {
            throw new BusinessException("至少选择一个维度或指标");
        }

        List<String> selections = new ArrayList<>();
        Set<String> selectedNames = new HashSet<>();
        for (String name : dimensions) {
            FieldDefinition field = requireField(fields, deniedFields, name, "DIMENSION");
            selections.add(quote(plugin, field.columnName()) + " AS " + alias(plugin, name));
            selectedNames.add(name);
        }
        for (String name : metrics) {
            FieldDefinition field = requireField(fields, deniedFields, name, "METRIC");
            String aggregation = field.aggregation().toUpperCase(Locale.ROOT);
            if (!AGGREGATIONS.contains(aggregation)) {
                throw new BusinessException("指标聚合方式不合法");
            }
            selections.add(aggregation + "(" + quote(plugin, field.columnName()) + ") AS " + alias(plugin, name));
            selectedNames.add(name);
        }

        List<Object> parameters = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        if (request.filter() != null) {
            predicates.add(compileFilter(request.filter(), fields, deniedFields, parameters, plugin));
        }
        for (QueryRequest.FilterNode rowRule : safeList(rowRules)) {
            predicates.add(compileFilter(rowRule, fields, Set.of(), parameters, plugin));
        }

        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", selections))
                .append(" FROM ").append(fromClause(dataset, plugin));
        if (!predicates.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", predicates));
        }
        if (!dimensions.isEmpty() && !metrics.isEmpty()) {
            sql.append(" GROUP BY ");
            sql.append(dimensions.stream()
                    .map(name -> quote(plugin, fields.get(name).columnName()))
                    .reduce((a, b) -> a + ", " + b).orElseThrow());
        }
        if (request.sorts() != null && !request.sorts().isEmpty()) {
            List<String> sorts = request.sorts().stream().map(sort -> {
                if (!selectedNames.contains(sort.field())) {
                    throw new BusinessException("排序字段必须在选择字段中");
                }
                String direction = "DESC".equalsIgnoreCase(sort.direction()) ? "DESC" : "ASC";
                return alias(plugin, sort.field()) + " " + direction;
            }).toList();
            sql.append(" ORDER BY ").append(String.join(", ", sorts));
        }
        String unlimitedSql = sql.toString();
        List<Object> countParameters = List.copyOf(parameters);
        String countSql = "SELECT COUNT(*) FROM (" + unlimitedSql + ") omni_cnt";
        sql.append(' ').append(plugin.limitPlaceholder());
        parameters.add(request.limit() == null ? 50_000 : request.limit());
        return new CompiledQuery(sql.toString(), List.copyOf(parameters), countSql, countParameters);
    }

    /**
     * 编译字段去重查询：{@code SELECT DISTINCT col ... LIMIT ?}。
     *
     * @param dataset      数据集定义
     * @param fieldName    语义字段名
     * @param limit        最大返回行数
     * @param deniedFields 当前用户不可访问的字段
     * @param dialect      目标方言
     * @return SQL 与参数（含 limit）
     */
    public CompiledQuery compileDistinct(DatasetDefinition dataset, String fieldName, int limit,
                                         Set<String> deniedFields, DialectPlugin dialect) {
        DialectPlugin plugin = dialect == null ? new MysqlDialectPlugin() : dialect;
        Map<String, FieldDefinition> fields = new HashMap<>();
        dataset.fields().forEach(field -> fields.put(field.name(), field));
        FieldDefinition field = requireField(fields, deniedFields == null ? Set.of() : deniedFields, fieldName, null);
        String column = quote(plugin, field.columnName());
        String sql = "SELECT DISTINCT " + column + " AS " + alias(plugin, fieldName)
                + " FROM " + fromClause(dataset, plugin)
                + " WHERE " + column + " IS NOT NULL"
                + " ORDER BY " + column
                + " " + plugin.limitPlaceholder();
        int capped = Math.max(1, limit);
        return new CompiledQuery(sql, List.of(capped));
    }

    /**
     * 生成 FROM 子句，支持物理表或 SQL 模型定义。
     *
     * @param dataset 数据集物理位置或 SQL 定义
     * @param dialect 目标数据库方言
     * @return 带引号的 FROM 表达式
     */
    private String fromClause(DatasetDefinition dataset, DialectPlugin dialect) {
        if (dataset.definitionSql() != null && !dataset.definitionSql().isBlank()) {
            return "(" + dataset.definitionSql().trim().replaceAll(";\\s*$", "") + ") AS "
                    + dialect.quoteIdentifier("_model");
        }
        if (dataset.schemaName() == null || dataset.tableName() == null) {
            throw new BusinessException("表模型缺少 schema 或 table");
        }
        return quote(dialect, dataset.schemaName()) + "." + quote(dialect, dataset.tableName());
    }

    /**
     * 递归编译过滤节点为 SQL 谓词，并将参数值追加到参数列表。
     *
     * @param node         过滤条件节点
     * @param fields       可用字段映射
     * @param deniedFields 禁止访问的字段名
     * @param parameters   输出参数列表
     * @param dialect      目标数据库方言
     * @return SQL 谓词片段
     */
    private String compileFilter(QueryRequest.FilterNode node, Map<String, FieldDefinition> fields,
                                 Set<String> deniedFields, List<Object> parameters, DialectPlugin dialect) {
        if (node.children() != null && !node.children().isEmpty()) {
            String logic = "OR".equalsIgnoreCase(node.logic()) ? " OR " : " AND ";
            return node.children().stream()
                    .map(child -> compileFilter(child, fields, deniedFields, parameters, dialect))
                    .reduce((left, right) -> "(" + left + logic + right + ")")
                    .orElseThrow(() -> new BusinessException("过滤组合不能为空"));
        }
        FieldDefinition field = requireField(fields, deniedFields, node.field(), null);
        String operator = node.operator() == null ? "" : node.operator().toUpperCase(Locale.ROOT);
        if (!OPERATORS.contains(operator)) {
            throw new BusinessException("过滤运算符不合法");
        }
        String column = quote(dialect, field.columnName());
        return switch (operator) {
            case "IS_NULL" -> column + " IS NULL";
            case "NOT_NULL" -> column + " IS NOT NULL";
            case "IN" -> compileIn(column, node.value(), parameters);
            default -> {
                parameters.add(node.value());
                yield column + " " + sqlOperator(operator) + " ?";
            }
        };
    }

    /**
     * 编译 IN 运算符的占位符列表，并将集合元素追加到参数列表。
     *
     * @param column     已引用的列表达式
     * @param value      IN 条件的值，须为非空集合
     * @param parameters 输出参数列表
     * @return IN 谓词片段
     */
    private String compileIn(String column, Object value, List<Object> parameters) {
        if (!(value instanceof Collection<?> values) || values.isEmpty()) {
            throw new BusinessException("IN 过滤值必须是非空数组");
        }
        parameters.addAll(values);
        return column + " IN (" + String.join(", ", java.util.Collections.nCopies(values.size(), "?")) + ")";
    }

    /**
     * 将语义过滤运算符映射为 SQL 运算符符号。
     *
     * @param operator 大写的语义运算符
     * @return SQL 运算符
     */
    private String sqlOperator(String operator) {
        return switch (operator) {
            case "EQ" -> "=";
            case "NE" -> "<>";
            case "GT" -> ">";
            case "GTE" -> ">=";
            case "LT" -> "<";
            case "LTE" -> "<=";
            case "LIKE" -> "LIKE";
            default -> throw new BusinessException("过滤运算符不合法");
        };
    }

    /**
     * 校验字段存在、未被拒绝且语义类型匹配，并校验物理列名合法。
     *
     * @param fields       可用字段映射
     * @param denied       禁止访问的字段名
     * @param name         请求的语义字段名
     * @param expectedType 期望的字段语义类型，为 null 时不校验类型
     * @return 通过校验的字段定义
     */
    private FieldDefinition requireField(Map<String, FieldDefinition> fields, Set<String> denied,
                                         String name, String expectedType) {
        FieldDefinition field = fields.get(name);
        if (field == null || denied.contains(name)) {
            throw new BusinessException("字段不在允许的元数据白名单中：" + name);
        }
        if (expectedType != null && !expectedType.equals(field.fieldType())) {
            throw new BusinessException("字段语义类型不匹配：" + name);
        }
        requireIdentifier(field.columnName());
        return field;
    }

    /**
     * 校验标识符后按方言规则引用列名或表名。
     *
     * @param dialect 目标数据库方言
     * @param value   待引用的标识符
     * @return 带引号的标识符
     */
    private String quote(DialectPlugin dialect, String value) {
        requireIdentifier(value);
        return dialect.quoteIdentifier(value);
    }

    /**
     * 校验元数据标识符仅包含允许的字符。
     *
     * @param value 待校验的标识符
     */
    private void requireIdentifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new BusinessException("元数据标识符不合法");
        }
    }

    /**
     * 校验字段别名合法并按方言规则引用。
     *
     * @param dialect 目标数据库方言
     * @param value   字段别名
     * @return 带引号的别名
     */
    private String alias(DialectPlugin dialect, String value) {
        if (value == null || !value.matches("[A-Za-z0-9_\\u4e00-\\u9fa5]+")) {
            throw new BusinessException("字段名称不合法");
        }
        return dialect.quoteIdentifier(value);
    }

    /**
     * 将 null 列表转换为空不可变列表。
     *
     * @param values 原始列表，可为 null
     * @return 非 null 的列表视图
     */
    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 数据集编译所需的物理位置、SQL 定义和字段白名单。
     *
     * @param schemaName    数据库模式名称
     * @param tableName     物理表名称
     * @param definitionSql SQL 模型定义
     * @param fields        可用于编译查询的字段定义
     */
    public record DatasetDefinition(String schemaName, String tableName, String definitionSql,
                                    List<FieldDefinition> fields) {
        /**
         * 兼容仅物理表的构造。
         *
         * @param schemaName 模式
         * @param tableName  表
         * @param fields     字段
         */
        public DatasetDefinition(String schemaName, String tableName, List<FieldDefinition> fields) {
            this(schemaName, tableName, null, fields);
        }
    }

    /**
     * 语义字段与物理列的映射定义。
     *
     * @param name        对外使用的语义字段名
     * @param columnName  数据库物理列名
     * @param fieldType   字段语义类型
     * @param aggregation 指标聚合方式
     */
    public record FieldDefinition(String name, String columnName, String fieldType, String aggregation) {
    }

    /**
     * 编译完成的参数化查询。
     *
     * @param sql              包含占位符的 SQL 文本（明细查询，可含 LIMIT）
     * @param parameters       按占位符顺序排列的参数
     * @param countSql         用于真实总数的 COUNT SQL；为 null 时由执行器对明细 SQL 包装
     * @param countParameters  COUNT 查询参数
     */
    public record CompiledQuery(String sql, List<Object> parameters, String countSql, List<Object> countParameters) {
        /**
         * 无独立 COUNT SQL 时的便捷构造（如 distinct 查询）。
         */
        public CompiledQuery(String sql, List<Object> parameters) {
            this(sql, parameters, null, null);
        }
    }
}
