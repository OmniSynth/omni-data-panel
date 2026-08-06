package com.omni.panel.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class NamedSqlExpanderTest {

    @Test
    void 命名占位展开为问号() {
        NamedSqlExpander.Expanded expanded = NamedSqlExpander.expand(
                "SELECT * FROM t WHERE a = :channel_id AND b >= :start_date",
                List.of(),
                Map.of("channel_id", 93, "start_date", "2026-08-04"));
        assertThat(expanded.sql()).isEqualTo("SELECT * FROM t WHERE a = ? AND b >= ?");
        assertThat(expanded.parameters()).containsExactly(93, "2026-08-04");
        assertThat(expanded.names()).containsExactly("channel_id", "start_date");
    }

    @Test
    void 同名多次各占一位取值相同() {
        NamedSqlExpander.Expanded expanded = NamedSqlExpander.expand(
                "SELECT * FROM t WHERE x = :id OR y = :id",
                null,
                Map.of("id", 1));
        assertThat(expanded.sql()).isEqualTo("SELECT * FROM t WHERE x = ? OR y = ?");
        assertThat(expanded.parameters()).containsExactly(1, 1);
    }

    @Test
    void 字符串内冒号与双冒号忽略() {
        NamedSqlExpander.Expanded expanded = NamedSqlExpander.expand(
                "SELECT ':not_param' AS a, col::text, :real FROM t",
                List.of(),
                Map.of("real", "ok"));
        assertThat(expanded.sql()).isEqualTo("SELECT ':not_param' AS a, col::text, ? FROM t");
        assertThat(expanded.parameters()).containsExactly("ok");
    }

    @Test
    void 命名与位置混用() {
        NamedSqlExpander.Expanded expanded = NamedSqlExpander.expand(
                "SELECT * FROM t WHERE a = ? AND b = :name AND c = ?",
                List.of("pos1", "pos2"),
                Map.of("name", "n"));
        assertThat(expanded.sql()).isEqualTo("SELECT * FROM t WHERE a = ? AND b = ? AND c = ?");
        assertThat(expanded.parameters()).containsExactly("pos1", "n", "pos2");
    }

    @Test
    void 缺少命名参数报错() {
        assertThatThrownBy(() -> NamedSqlExpander.expand(
                "SELECT * FROM t WHERE a = :missing", List.of(), Map.of()))
                .hasMessageContaining("missing");
    }

    @Test
    void extractNames去重保序() {
        assertThat(NamedSqlExpander.extractNames(
                "WHERE a=:b AND c=:a AND d=:b AND e=':x'"))
                .containsExactly("b", "a");
    }
}
