package com.omni.panel.query;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.dialect.DialectPlugin;

/**
 * 校验待执行 SQL 是否符合只读查询策略。
 *
 * <p>该组件拒绝文件导出、加锁读取以及多语句或非查询语句。它是原生 SQL 特权检查之外
 * 的语句级安全边界，不负责授予原生 SQL 执行权限。</p>
 */
@Component
public class SqlPolicyGuard {
    private static final Logger log = LoggerFactory.getLogger(SqlPolicyGuard.class);
    private static final List<Pattern> COMMON_FORBIDDEN = List.of(
            Pattern.compile("(?s).*\\binto\\s+(out|dump)file\\b.*"),
            Pattern.compile("(?s).*\\bfor\\s+update\\b.*")
    );

    /**
     * 解析并确认 SQL 仅包含一条 {@code SELECT} 或 {@code WITH SELECT}，且不含通用危险操作。
     *
     * @param sql 待校验的 SQL 文本
     */
    public void validate(String sql) {
        validate(sql, null);
    }

    /**
     * 在通用只读策略之外，叠加方言特有禁止项。
     *
     * @param sql     待校验的 SQL 文本
     * @param dialect 方言插件，可为 null
     */
    public void validate(String sql, DialectPlugin dialect) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("SQL 不能为空");
        }
        String normalized = sql.toLowerCase(Locale.ROOT);
        for (Pattern pattern : COMMON_FORBIDDEN) {
            if (pattern.matcher(normalized).matches()) {
                throw new BusinessException("SQL 包含禁止的只读操作");
            }
        }
        if (dialect != null) {
            for (Pattern pattern : dialect.forbiddenSqlPatterns()) {
                if (pattern.matcher(normalized).matches()) {
                    throw new BusinessException("SQL 包含禁止的只读操作");
                }
            }
        }
        try {
            var statements = CCJSqlParserUtil.parseStatements(sql).getStatements();
            if (statements.size() != 1 || !(statements.getFirst() instanceof Select)) {
                log.warn("SQL 策略校验失败：非单条 SELECT。statements={} sql=\n{}",
                        statements.size(), sql);
                throw new BusinessException("仅允许单条 SELECT 或 WITH SELECT");
            }
        } catch (JSQLParserException exception) {
            log.warn("SQL 解析失败：{}\n出错附近：\n{}\nsql=\n{}",
                    rootMessage(exception), snippetAroundError(sql, rootMessage(exception)), sql);
            throw new BusinessException("SQL 解析失败");
        }
    }

    /** 从解析错误信息中提取行号，打印前后各 3 行便于定位。 */
    private static String snippetAroundError(String sql, String message) {
        if (sql == null || message == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("(?i)at line (\\d+)").matcher(message);
        if (!matcher.find()) {
            return "";
        }
        int lineNo;
        try {
            lineNo = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return "";
        }
        String[] lines = sql.split("\\R", -1);
        int from = Math.max(1, lineNo - 3);
        int to = Math.min(lines.length, lineNo + 3);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            sb.append(i == lineNo ? ">>> " : "    ");
            sb.append(i).append(": ").append(lines[i - 1]).append('\n');
        }
        return sb.toString();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? throwable.toString() : message;
    }
}
