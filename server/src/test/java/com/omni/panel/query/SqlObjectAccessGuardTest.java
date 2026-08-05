package com.omni.panel.query;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Set;

import org.junit.jupiter.api.Test;
import com.omni.panel.common.BusinessException;
import com.omni.panel.service.DataSourceObjectAclService;
import com.omni.panel.service.DataSourceObjectAclService.EffectiveDenies;

class SqlObjectAccessGuardTest {
    private final SqlObjectAccessGuard guard = new SqlObjectAccessGuard(mock(DataSourceObjectAclService.class));

    @Test
    void 无拒绝规则时允许普通查询() {
        EffectiveDenies denies = EffectiveDenies.none();
        assertThatCode(() -> guard.validate(1L, "SELECT id FROM demo.orders", "demo", denies))
                .doesNotThrowAnyException();
    }

    @Test
    void 拒绝表时禁止引用() {
        EffectiveDenies denies = new EffectiveDenies(
                Set.of(DataSourceObjectAclService.tableKey("demo", "salary")),
                Set.of(), true);
        assertThatThrownBy(() -> guard.validate(1L, "SELECT * FROM demo.salary", "demo", denies))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("salary");
    }

    @Test
    void 拒绝列时禁止选择该列() {
        EffectiveDenies denies = new EffectiveDenies(
                Set.of(),
                Set.of(DataSourceObjectAclService.columnKey("demo", "employee", "ssn")),
                true);
        assertThatThrownBy(() -> guard.validate(1L, "SELECT ssn FROM demo.employee", "demo", denies))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ssn");
        assertThatCode(() -> guard.validate(1L, "SELECT name FROM demo.employee", "demo", denies))
                .doesNotThrowAnyException();
    }

    @Test
    void 禁止系统元数据库() {
        EffectiveDenies denies = EffectiveDenies.none();
        assertThatThrownBy(() -> guard.validate(1L, "SELECT * FROM information_schema.tables", "demo", denies))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统元数据库");
    }

    @Test
    void 存在列拒绝时禁止星号() {
        EffectiveDenies denies = new EffectiveDenies(
                Set.of(),
                Set.of(DataSourceObjectAclService.columnKey("demo", "employee", "ssn")),
                true);
        assertThatThrownBy(() -> guard.validate(1L, "SELECT * FROM demo.employee", "demo", denies))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SELECT *");
    }
}
