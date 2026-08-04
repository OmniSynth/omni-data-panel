package com.omni.panel.query;

import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.dialect.DialectPlugin;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 校验待执行 SQL 是否符合只读查询策略。
 *
 * <p>该组件拒绝文件导出、加锁读取以及多语句或非查询语句。它是原生 SQL 特权检查之外
 * 的语句级安全边界，不负责授予原生 SQL 执行权限。</p>
 */
@Component
public class SqlPolicyGuard {
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
     * @param sql 待校验的 SQL 文本
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
                throw new BusinessException("仅允许单条 SELECT 或 WITH SELECT");
            }
        } catch (JSQLParserException exception) {
            throw new BusinessException("SQL 解析失败");
        }
    }
}
