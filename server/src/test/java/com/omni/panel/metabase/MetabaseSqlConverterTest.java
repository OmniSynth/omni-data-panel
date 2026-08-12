package com.omni.panel.metabase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetabaseSqlConverterTest {
    @Test
    void 无Metabase语法时changed为false() {
        String sql = "SELECT * FROM t WHERE id = :id";
        MetabaseSqlConverter.Result result = MetabaseSqlConverter.convert(sql);
        assertThat(result.changed()).isFalse();
        assertThat(result.sql()).isEqualTo(sql);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void 将变量转为命名占位() {
        MetabaseSqlConverter.Result result = MetabaseSqlConverter.convert(
                "SELECT * FROM t WHERE id = {{user_id}}");
        assertThat(result.changed()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT * FROM t WHERE id = :user_id");
        assertThat(result.defaults()).isEmpty();
    }

    @Test
    void 提取默认值() {
        MetabaseSqlConverter.Result result = MetabaseSqlConverter.convert(
                "SELECT * FROM t WHERE d >= {{start_date=2026-01-01}}");
        assertThat(result.sql()).isEqualTo("SELECT * FROM t WHERE d >= :start_date");
        assertThat(result.defaults()).containsEntry("start_date", "2026-01-01");
    }

    @Test
    void 展开可选块并转换内层变量() {
        MetabaseSqlConverter.Result result = MetabaseSqlConverter.convert(
                "SELECT * FROM t WHERE 1=1\n[[ AND city = {{city_filter}} ]]\nORDER BY 1");
        assertThat(result.changed()).isTrue();
        assertThat(result.sql()).contains("-- omni: 原 Metabase 可选块，已始终保留");
        assertThat(result.sql()).doesNotContain("[[").doesNotContain("]]");
        assertThat(result.sql()).contains("AND city = :city_filter");
    }

    @Test
    void 无AND的可选条件自动补AND() {
        MetabaseSqlConverter.Result result = MetabaseSqlConverter.convert(
                """
                        SELECT * FROM t WHERE a.type = 3
                        [[and a.times in ({{times}})]]
                        [[a.add_service_order_id={{order_id}}]]
                        GROUP BY 1
                        """);
        assertThat(result.sql()).doesNotContain("[[").doesNotContain("]]");
        assertThat(result.sql()).contains("and a.times in (:times)");
        assertThat(result.sql()).contains("AND a.add_service_order_id=:order_id");
    }

    @Test
    void 展开嵌套卡片引用为子查询() {
        MetabaseSqlConverter.CardExpandResult expanded = MetabaseSqlConverter.replaceCardRefs(
                "select a.x FROM {{#69-}} a WHERE a.y in ({{channel}})",
                id -> {
                    assertThat(id).isEqualTo(69L);
                    return "(" + MetabaseSqlConverter.stripTrailingSemicolons(
                            "SELECT 1 AS x, 'c' AS y;") + ")";
                });
        assertThat(expanded.error()).isNull();
        assertThat(expanded.sql()).isEqualTo(
                "select a.x FROM (SELECT 1 AS x, 'c' AS y) a WHERE a.y in ({{channel}})");
    }

    @Test
    void 去掉末尾分号() {
        assertThat(MetabaseSqlConverter.stripTrailingSemicolons("SELECT 1;\n;  \n"))
                .isEqualTo("SELECT 1");
        assertThat(MetabaseSqlConverter.convert("SELECT {{id}};").sql()).isEqualTo("SELECT :id");
    }

    @Test
    void 去掉分号后注释前的语句分号() {
        String sql = """
                SELECT 1 AS x
                ORDER BY 1;
                -- trailing note
                """;
        String cleaned = MetabaseSqlConverter.removeSemicolonsOutsideStrings(sql);
        assertThat(cleaned).doesNotContain(";");
        assertThat(cleaned).contains("-- trailing note");
        assertThat("(" + cleaned.stripTrailing() + ")").doesNotContain(";");
    }

    @Test
    void 保留字符串内分号() {
        assertThat(MetabaseSqlConverter.removeSemicolonsOutsideStrings("SELECT ';' AS x;"))
                .isEqualTo("SELECT ';' AS x");
    }

    @Test
    void 压缩CTE之间多余空行() {
        String sql = "WITH t1 AS (SELECT 1 x FROM t),\n\n\nt2 AS (SELECT 1 y) SELECT * FROM t1";
        String cleaned = MetabaseSqlConverter.collapseExcessBlankLines(sql);
        assertThat(cleaned).isEqualTo("WITH t1 AS (SELECT 1 x FROM t),\n\nt2 AS (SELECT 1 y) SELECT * FROM t1");
        assertThat(MetabaseSqlConverter.convert(sql).sql()).doesNotContain("\n\n\n");
    }

    @Test
    void 解析卡片引用ID() {
        assertThat(MetabaseSqlConverter.parseCardRefId("#69-")).isEqualTo(69L);
        assertThat(MetabaseSqlConverter.parseCardRefId("#69-foo-bar")).isEqualTo(69L);
        assertThat(MetabaseSqlConverter.parseCardRefId("channel")).isNull();
    }

    @Test
    void 字符串内模板不替换() {
        MetabaseSqlConverter.Result result = MetabaseSqlConverter.convert(
                "SELECT '{{user_id}}' AS tip, id FROM t WHERE id = {{user_id}}");
        assertThat(result.sql()).isEqualTo(
                "SELECT '{{user_id}}' AS tip, id FROM t WHERE id = :user_id");
    }

    @Test
    void 不支持标签保留并告警() {
        MetabaseSqlConverter.Result result = MetabaseSqlConverter.convert(
                "SELECT * FROM {{#123}} x WHERE a = {{snippet: common}} AND b = {{ok}}");
        assertThat(result.sql()).contains("{{#123}}").contains("{{snippet: common}}").contains(":ok");
        assertThat(result.warnings()).hasSizeGreaterThanOrEqualTo(2);
    }
}
