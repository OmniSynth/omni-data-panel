package com.omni.panel.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.omni.panel.common.BusinessException;

/**
 * 将 {@code :name} 命名占位展开为 JDBC {@code ?}，并按出现顺序组装参数值。
 * 保留裸 {@code ?}；与命名混用时按从左到右分别消费 map / list。
 */
public final class NamedSqlExpander {
    private NamedSqlExpander() {
    }

    /**
     * 展开结果。
     *
     * @param sql        仅含 {@code ?} 的 JDBC SQL
     * @param parameters 与占位符一一对应的参数值
     * @param names      每个占位对应的逻辑名；裸 {@code ?} 为 {@code null}
     */
    public record Expanded(String sql, List<Object> parameters, List<String> names) {
    }

    /**
     * 展开命名占位并绑定参数。
     *
     * @param sql        原始 SQL（可含 {@code :name} 与 {@code ?}）
     * @param positional 裸 {@code ?} 的顺序参数，可为 {@code null}
     * @param named      命名参数映射，可为 {@code null}
     * @return JDBC SQL 与参数列表
     */
    public static Expanded expand(String sql, List<Object> positional, Map<String, Object> named) {
        if (sql == null) {
            throw new BusinessException("SQL 不能为空");
        }
        List<Object> positionalValues = positional == null ? List.of() : positional;
        Map<String, Object> namedValues = named == null ? Map.of() : named;
        StringBuilder out = new StringBuilder(sql.length());
        List<Object> bound = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int positionalIndex = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (inSingle) {
                out.append(ch);
                if (ch == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        out.append(sql.charAt(++i));
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }
            if (inDouble) {
                out.append(ch);
                if (ch == '"') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '"') {
                        out.append(sql.charAt(++i));
                    } else {
                        inDouble = false;
                    }
                }
                continue;
            }
            if (inBacktick) {
                out.append(ch);
                if (ch == '`') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '`') {
                        out.append(sql.charAt(++i));
                    } else {
                        inBacktick = false;
                    }
                }
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

            if (ch == '?') {
                if (positionalIndex >= positionalValues.size()) {
                    throw new BusinessException("SQL 位置参数不足，缺少第 " + (positionalIndex + 1) + " 个 ? 的值");
                }
                out.append('?');
                bound.add(positionalValues.get(positionalIndex++));
                names.add(null);
                continue;
            }

            if (ch == ':') {
                // PostgreSQL ::type 以及孤立冒号不视为命名参数
                if (i + 1 < sql.length() && sql.charAt(i + 1) == ':') {
                    out.append("::");
                    i += 1;
                    continue;
                }
                int start = i + 1;
                if (start >= sql.length() || !isIdentStart(sql.charAt(start))) {
                    out.append(ch);
                    continue;
                }
                int end = start + 1;
                while (end < sql.length() && isIdentPart(sql.charAt(end))) {
                    end += 1;
                }
                String name = sql.substring(start, end);
                if (!namedValues.containsKey(name)) {
                    throw new BusinessException("缺少命名参数：" + name);
                }
                out.append('?');
                bound.add(namedValues.get(name));
                names.add(name);
                i = end - 1;
                continue;
            }

            out.append(ch);
        }

        return new Expanded(out.toString(), Collections.unmodifiableList(bound), Collections.unmodifiableList(names));
    }

    /**
     * 按出现顺序提取命名占位（去重保序），忽略字符串与 {@code ::}。
     *
     * @param sql 原始 SQL
     * @return 参数名列表
     */
    public static List<String> extractNames(String sql) {
        if (sql == null || sql.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (inSingle) {
                if (ch == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        i += 1;
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }
            if (inDouble) {
                if (ch == '"') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '"') {
                        i += 1;
                    } else {
                        inDouble = false;
                    }
                }
                continue;
            }
            if (inBacktick) {
                if (ch == '`') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '`') {
                        i += 1;
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
            if (ch == ':' && i + 1 < sql.length() && sql.charAt(i + 1) == ':') {
                i += 1;
                continue;
            }
            if (ch == ':' && i + 1 < sql.length() && isIdentStart(sql.charAt(i + 1))) {
                int start = i + 1;
                int end = start + 1;
                while (end < sql.length() && isIdentPart(sql.charAt(end))) {
                    end += 1;
                }
                String name = sql.substring(start, end);
                if (!names.contains(name)) {
                    names.add(name);
                }
                i = end - 1;
            }
        }
        return List.copyOf(names);
    }

    private static boolean isIdentStart(char ch) {
        return (ch >= 'A' && ch <= 'Z')
                || (ch >= 'a' && ch <= 'z')
                || ch == '_';
    }

    private static boolean isIdentPart(char ch) {
        return isIdentStart(ch) || (ch >= '0' && ch <= '9');
    }
}
