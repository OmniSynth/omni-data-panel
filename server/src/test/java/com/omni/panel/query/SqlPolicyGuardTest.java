package com.omni.panel.query;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.omni.panel.common.BusinessException;
import com.omni.panel.datasource.dialect.MysqlDialectPlugin;

class SqlPolicyGuardTest {
    private final SqlPolicyGuard guard = new SqlPolicyGuard();
    private final MysqlDialectPlugin mysql = new MysqlDialectPlugin();

    @Test
    @DisplayName("允许单条 SELECT 和 WITH SELECT")
    void allowsReadOnlyQueries() {
        assertDoesNotThrow(() -> guard.validate("SELECT id FROM users"));
        assertDoesNotThrow(() -> guard.validate("WITH active AS (SELECT id FROM users) SELECT id FROM active"));
    }

    @Test
    @DisplayName("拒绝写操作、多语句、文件导出和锁")
    void rejectsUnsafeQueries() {
        assertThrows(BusinessException.class, () -> guard.validate("DELETE FROM users"));
        assertThrows(BusinessException.class, () -> guard.validate("SELECT 1; SELECT 2"));
        assertThrows(BusinessException.class, () -> guard.validate("SELECT * FROM users INTO OUTFILE '/tmp/a'"));
        assertThrows(BusinessException.class, () -> guard.validate("SELECT * FROM users FOR UPDATE"));
        assertThrows(BusinessException.class,
                () -> guard.validate("SELECT * FROM users LOCK IN SHARE MODE", mysql));
    }
}
