package com.omni.panel.metabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 Metabase 模板 SQL 转为 Omni {@code :name} 命名占位写法。
 * 可选块 {@code [[...]]} 始终保留内层；Field Filter / Snippet / 嵌套卡片仅告警。
 */
public final class MetabaseSqlConverter {
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern CARD_REF_ID = Pattern.compile("^#(\\d+)");
    private static final String OPTIONAL_COMMENT = "-- omni: 原 Metabase 可选块，已始终保留";

    private MetabaseSqlConverter() {
    }

    /**
     * 转换结果。
     *
     * @param sql      转换后 SQL
     * @param defaults 从 {@code {{name=默认}}} 提取的默认值
     * @param warnings 告警（不支持的标签等）
     * @param changed  是否相对原文有改动
     */
    public record Result(String sql, Map<String, String> defaults, List<String> warnings, boolean changed) {
    }

    /**
     * Metabase 模板 SQL → Omni 命名占位。
     *
     * @param sql 原始 SQL
     * @return 转换结果
     */
    public static Result convert(String sql) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> defaults = new LinkedHashMap<>();
        if (sql == null || sql.isEmpty()) {
            return new Result(sql == null ? "" : sql, defaults, List.of(), false);
        }
        ExpandResult optional = expandOptionalBlocks(sql, warnings);
        ExpandResult mustache = convertMustacheTags(optional.sql(), defaults, warnings);
        String next = collapseExcessBlankLines(removeSemicolonsOutsideStrings(mustache.sql())).stripTrailing();
        Set<String> seen = new LinkedHashSet<>();
        List<String> unique = new ArrayList<>();
        for (String warning : warnings) {
            if (seen.add(warning)) {
                unique.add(warning);
            }
        }
        return new Result(next, Map.copyOf(defaults), List.copyOf(unique), !next.equals(sql));
    }

    /**
     * JSQLParser 5.x 在 {@code ),} 后连续两个及以上空行时会误报 {@code ST_SEMICOLON}。
     * 将 3 个及以上连续换行压缩为 2 个（最多保留一个空行）。
     */
    public static String collapseExcessBlankLines(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql == null ? "" : sql;
        }
        return sql.replaceAll("\\R{3,}", "\n\n");
    }

    /**
     * 去掉语句末尾分号（及尾部空白）。嵌套卡片内联为 {@code (subquery)} 时分号会导致解析失败。
     */
    public static String stripTrailingSemicolons(String sql) {
        return removeSemicolonsOutsideStrings(sql).stripTrailing();
    }

    /**
     * 删除字符串字面量之外的 {@code ;}。Metabase 卡片均为单语句，分号仅作终止符；
     * 内联为子查询时，即便分号后还有注释/空行也必须去掉。
     */
    public static String removeSemicolonsOutsideStrings(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql == null ? "" : sql;
        }
        StringBuilder out = new StringBuilder(sql.length());
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (inLineComment) {
                out.append(ch);
                if (ch == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                out.append(ch);
                if (ch == '*' && next == '/') {
                    out.append(next);
                    i++;
                    inBlockComment = false;
                }
                continue;
            }
            if (inSingle) {
                out.append(ch);
                if (ch == '\'') {
                    if (next == '\'') {
                        out.append(next);
                        i++;
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }
            if (inDouble) {
                out.append(ch);
                if (ch == '"') {
                    if (next == '"') {
                        out.append(next);
                        i++;
                    } else {
                        inDouble = false;
                    }
                }
                continue;
            }
            if (inBacktick) {
                out.append(ch);
                if (ch == '`') {
                    if (next == '`') {
                        out.append(next);
                        i++;
                    } else {
                        inBacktick = false;
                    }
                }
                continue;
            }
            if (ch == '-' && next == '-') {
                out.append(ch).append(next);
                i++;
                inLineComment = true;
                continue;
            }
            if (ch == '/' && next == '*') {
                out.append(ch).append(next);
                i++;
                inBlockComment = true;
                continue;
            }
            if (ch == '\'') {
                inSingle = true;
                out.append(ch);
                continue;
            }
            if (ch == '"') {
                inDouble = true;
                out.append(ch);
                continue;
            }
            if (ch == '`') {
                inBacktick = true;
                out.append(ch);
                continue;
            }
            if (ch == ';') {
                continue;
            }
            out.append(ch);
        }
        return out.toString();
    }

    /**
     * 解析 {@code {{#123}}} / {@code {{#123-slug}}} 中的卡片 ID；非卡片引用返回 null。
     */
    public static Long parseCardRefId(String rawTagBody) {
        if (rawTagBody == null) {
            return null;
        }
        Matcher matcher = CARD_REF_ID.matcher(rawTagBody.trim());
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }

    /**
     * 将 {@code {{#id}}} / {@code {{#id-slug}}} 替换为 {@code resolver} 返回的子查询文本。
     * resolver 应返回已含外层括号的 SQL，例如 {@code (SELECT ...)}。
     *
     * @param sql      原始 SQL
     * @param resolver 按卡片 ID 解析子查询；返回 null 表示无法解析
     * @return 替换结果；若存在无法解析的卡片引用则 {@link CardExpandResult#error()} 非空
     */
    public static CardExpandResult replaceCardRefs(String sql, LongFunction<String> resolver) {
        if (sql == null || sql.isEmpty()) {
            return new CardExpandResult(sql == null ? "" : sql, null);
        }
        StringBuilder parts = new StringBuilder();
        int last = 0;
        List<Integer> opens = new ArrayList<>();
        scanOutsideStrings(sql, (i, ch) -> {
            if (ch == '{' && i + 1 < sql.length() && sql.charAt(i + 1) == '{') {
                opens.add(i);
            }
        });
        for (int open : opens) {
            if (open < last) {
                continue;
            }
            int contentStart = open + 2;
            int close = findClosingMustache(sql, contentStart);
            if (close < 0) {
                parts.append(sql.substring(last));
                return new CardExpandResult(parts.toString(), "存在未闭合的模板标签 {{...}}");
            }
            String raw = sql.substring(contentStart, close);
            Long cardId = parseCardRefId(raw);
            parts.append(sql, last, open);
            if (cardId != null) {
                String replacement = resolver.apply(cardId);
                if (replacement == null || replacement.isBlank()) {
                    return new CardExpandResult(sql, "无法展开嵌套卡片 #" + cardId);
                }
                parts.append(replacement.trim());
            } else {
                parts.append("{{").append(raw).append("}}");
            }
            last = close + 2;
        }
        parts.append(sql.substring(last));
        return new CardExpandResult(parts.toString(), null);
    }

    /**
     * 嵌套卡片展开结果。
     *
     * @param sql   展开后 SQL（失败时可能仍为原文）
     * @param error 失败原因，成功时为 null
     */
    public record CardExpandResult(String sql, String error) {
    }

    private record ExpandResult(String sql, boolean changed) {
    }

    private static ExpandResult expandOptionalBlocks(String sql, List<String> warnings) {
        String next = sql;
        boolean changed = false;
        while (true) {
            List<Integer> opens = collectOptionalOpens(next);
            if (opens.isEmpty()) {
                break;
            }
            int open = -1;
            int close = -1;
            for (int k = opens.size() - 1; k >= 0; k--) {
                int o = opens.get(k);
                int c = findClosingOptional(next, o + 2);
                if (c < 0) {
                    continue;
                }
                String inner = next.substring(o + 2, c);
                if (!collectOptionalOpens(inner).isEmpty()) {
                    continue;
                }
                open = o;
                close = c;
                break;
            }
            if (open < 0 || close < 0) {
                warnings.add("存在未闭合的 Metabase 可选块 [[...]]，已跳过");
                break;
            }
            String inner = next.substring(open + 2, close);
            String before = next.substring(0, open);
            String after = next.substring(close + 2);
            boolean needNewline = !before.isEmpty() && !before.matches("(?s).*\\n\\s*$");
            String prefix = (needNewline ? "\n" : "") + OPTIONAL_COMMENT + "\n";
            next = before + prefix + ensureOptionalConjunction(inner) + after;
            changed = true;
        }
        return new ExpandResult(next, changed);
    }

    /**
     * Metabase 可选块常写成 {@code [[col = {{x}}]]}（无 AND）；展开后接到 WHERE 子句会语法错误。
     * 已有 AND/OR 或以逗号开头（SELECT 列表）的保持原样。
     */
    private static String ensureOptionalConjunction(String inner) {
        String trimmed = inner.stripLeading();
        if (trimmed.isEmpty()) {
            return inner;
        }
        if (trimmed.charAt(0) == ',') {
            return inner;
        }
        if (trimmed.matches("(?is)^(and|or)\\b.*")) {
            return inner;
        }
        int leading = inner.length() - trimmed.length();
        return inner.substring(0, leading) + "AND " + trimmed;
    }

    private static ExpandResult convertMustacheTags(String sql, Map<String, String> defaults,
                                                    List<String> warnings) {
        StringBuilder parts = new StringBuilder();
        int last = 0;
        boolean changed = false;
        List<Integer> opens = new ArrayList<>();
        scanOutsideStrings(sql, (i, ch) -> {
            if (ch == '{' && i + 1 < sql.length() && sql.charAt(i + 1) == '{') {
                opens.add(i);
            }
        });
        for (int open : opens) {
            if (open < last) {
                continue;
            }
            int contentStart = open + 2;
            int close = findClosingMustache(sql, contentStart);
            if (close < 0) {
                warnings.add("存在未闭合的 Metabase 模板标签 {{...}}，已跳过");
                continue;
            }
            String raw = sql.substring(contentStart, close);
            TagKind tag = classifyTag(raw);
            parts.append(sql, last, open);
            if (tag instanceof TagKind.Variable variable) {
                parts.append(':').append(variable.name());
                if (variable.defaultValue() != null && !defaults.containsKey(variable.name())) {
                    defaults.put(variable.name(), variable.defaultValue());
                }
                changed = true;
            } else if (tag instanceof TagKind.Unsupported unsupported) {
                parts.append("{{").append(raw).append("}}");
                warnings.add(unsupported.reason());
            }
            last = close + 2;
        }
        parts.append(sql.substring(last));
        return new ExpandResult(parts.toString(), changed);
    }

    private sealed interface TagKind {
        record Variable(String name, String defaultValue) implements TagKind {
        }

        record Unsupported(String raw, String reason) implements TagKind {
        }
    }

    private static TagKind classifyTag(String raw) {
        String body = raw.trim();
        if (body.isEmpty()) {
            return new TagKind.Unsupported(raw, "空模板标签");
        }
        String lower = body.toLowerCase();
        if (body.startsWith("#")) {
            return new TagKind.Unsupported(body, "嵌套卡片/引用「" + body + "」不支持，请手改");
        }
        if (lower.startsWith("snippet:") || lower.contains("snippet:")) {
            return new TagKind.Unsupported(body, "Snippet「" + body + "」不支持，请手改");
        }
        if (body.matches("(?i)^(dimension|field|date|number|text|temporal-unit)\\s*:.*")) {
            return new TagKind.Unsupported(body, "Field Filter「" + body + "」不支持，请手改");
        }
        int eq = body.indexOf('=');
        String namePart = (eq >= 0 ? body.substring(0, eq) : body).trim();
        String defaultValue = eq >= 0 ? body.substring(eq + 1).trim() : null;
        if (!IDENT.matcher(namePart).matches()) {
            return new TagKind.Unsupported(body,
                    "非法参数名「" + (namePart.isEmpty() ? body : namePart) + "」，仅支持字母/数字/下划线");
        }
        if (eq < 0 && body.contains(":")) {
            return new TagKind.Unsupported(body, "特殊模板标签「" + body + "」不支持，请手改");
        }
        return new TagKind.Variable(namePart,
                defaultValue != null && !defaultValue.isEmpty() ? defaultValue : null);
    }

    private static List<Integer> collectOptionalOpens(String sql) {
        List<Integer> opens = new ArrayList<>();
        scanOutsideStrings(sql, (i, ch) -> {
            if (ch == '[' && i + 1 < sql.length() && sql.charAt(i + 1) == '[') {
                opens.add(i);
            }
        });
        return opens;
    }

    private static int findClosingOptional(String sql, int openEnd) {
        final int[] depth = {1};
        final int[] closeAt = {-1};
        String slice = sql.substring(openEnd);
        scanOutsideStrings(slice, (rel, ch) -> {
            if (closeAt[0] >= 0) {
                return;
            }
            int i = openEnd + rel;
            if (ch == '[' && i + 1 < sql.length() && sql.charAt(i + 1) == '[') {
                depth[0]++;
                return;
            }
            if (ch == ']' && i + 1 < sql.length() && sql.charAt(i + 1) == ']') {
                depth[0]--;
                if (depth[0] == 0) {
                    closeAt[0] = i;
                }
            }
        });
        return closeAt[0];
    }

    private static int findClosingMustache(String sql, int contentStart) {
        final int[] closeAt = {-1};
        String slice = sql.substring(contentStart);
        scanOutsideStrings(slice, (rel, ch) -> {
            if (closeAt[0] >= 0) {
                return;
            }
            int i = contentStart + rel;
            if (ch == '}' && i + 1 < sql.length() && sql.charAt(i + 1) == '}') {
                closeAt[0] = i;
            }
        });
        return closeAt[0];
    }

    @FunctionalInterface
    private interface CodeVisitor {
        void accept(int index, char ch);
    }

    private static void scanOutsideStrings(String sql, CodeVisitor onCode) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (inSingle) {
                if (ch == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }
            if (inDouble) {
                if (ch == '"') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '"') {
                        i++;
                    } else {
                        inDouble = false;
                    }
                }
                continue;
            }
            if (inBacktick) {
                if (ch == '`') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '`') {
                        i++;
                    } else {
                        inBacktick = false;
                    }
                }
                continue;
            }
            if (ch == '\'') {
                inSingle = true;
                continue;
            }
            if (ch == '"') {
                inDouble = true;
                continue;
            }
            if (ch == '`') {
                inBacktick = true;
                continue;
            }
            onCode.accept(i, ch);
        }
    }
}
