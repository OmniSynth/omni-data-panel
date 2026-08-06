package com.omni.panel.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.omni.panel.service.QueryService;

class QueryParameterApplierTest {
    private final QueryParameterApplier applier = new QueryParameterApplier(new ObjectMapper());

    @Test
    void 语义绑定覆盖同字段并AND合并() {
        QueryRequest.FilterNode existing = new QueryRequest.FilterNode(null, "region", "EQ", "华北", null);
        QueryRequest query = new QueryRequest(1L, List.of("region"), List.of("sales"), null,
                new QueryRequest.FilterNode("AND", null, null, null, List.of(existing)),
                List.of(), 100);
        QueryService.QuerySubmission submission = new QueryService.QuerySubmission(null, null, null, query);
        List<QueryParameterApplier.Binding> bindings = List.of(
                new QueryParameterApplier.Binding("region", "semantic", "region", "EQ", null),
                new QueryParameterApplier.Binding("city", "semantic", "city", "EQ", null));
        Map<String, Object> values = Map.of("region", "华东", "city", "上海");
        Map<String, QueryParameterApplier.ParameterMeta> metas = Map.of(
                "region", new QueryParameterApplier.ParameterMeta("region", "text", false),
                "city", new QueryParameterApplier.ParameterMeta("city", "text", false));

        QueryService.QuerySubmission applied = applier.apply(submission, bindings, values, metas);

        assertThat(applied.query()).isNotNull();
        assertThat(applied.query().filter().children()).hasSize(2);
        assertThat(applied.query().filter().children())
                .anySatisfy(node -> {
                    assertThat(node.field()).isEqualTo("region");
                    assertThat(node.value()).isEqualTo("华东");
                })
                .anySatisfy(node -> {
                    assertThat(node.field()).isEqualTo("city");
                    assertThat(node.value()).isEqualTo("上海");
                });
    }

    @Test
    void 空值非必填跳过绑定() {
        QueryRequest query = new QueryRequest(1L, List.of("region"), List.of("sales"), null, null, List.of(), 100);
        QueryService.QuerySubmission submission = new QueryService.QuerySubmission(null, null, null, query);
        List<QueryParameterApplier.Binding> bindings = List.of(
                new QueryParameterApplier.Binding("region", "semantic", "region", "EQ", null));

        QueryService.QuerySubmission applied = applier.apply(submission, bindings, Map.of(), Map.of(
                "region", new QueryParameterApplier.ParameterMeta("region", "text", false)));

        assertThat(applied.query().filter()).isNull();
    }

    @Test
    void dateRange展开为GTE与LTE() {
        QueryRequest query = new QueryRequest(1L, List.of("dt"), List.of("sales"), null, null, List.of(), 100);
        QueryService.QuerySubmission submission = new QueryService.QuerySubmission(null, null, null, query);
        List<QueryParameterApplier.Binding> bindings = List.of(
                new QueryParameterApplier.Binding("range", "semantic", "dt", "EQ", null));

        QueryService.QuerySubmission applied = applier.apply(submission, bindings,
                Map.of("range", Map.of("start", "2026-01-01", "end", "2026-01-31")),
                Map.of("range", new QueryParameterApplier.ParameterMeta("range", "date-range", false)));

        assertThat(applied.query().filter().children()).hasSize(2);
        assertThat(applied.query().filter().children())
                .anySatisfy(node -> assertThat(node.operator()).isEqualTo("GTE"))
                .anySatisfy(node -> assertThat(node.operator()).isEqualTo("LTE"));
    }

    @Test
    void SQL按下标写入参数() {
        QueryService.QuerySubmission submission =
                new QueryService.QuerySubmission(3L, "SELECT * FROM t WHERE a=? AND b=?", List.of(), null);
        List<QueryParameterApplier.Binding> bindings = List.of(
                new QueryParameterApplier.Binding("a", "sql", null, null, 0),
                new QueryParameterApplier.Binding("b", "sql", null, null, 1));

        QueryService.QuerySubmission applied = applier.apply(submission, bindings,
                Map.of("a", "x", "b", 10),
                Map.of(
                        "a", new QueryParameterApplier.ParameterMeta("a", "text", false),
                        "b", new QueryParameterApplier.ParameterMeta("b", "number", false)));

        assertThat(applied.parameters()).containsExactly("x", 10);
    }

    @Test
    void SQL拒绝集合参数() {
        QueryService.QuerySubmission submission =
                new QueryService.QuerySubmission(3L, "SELECT * FROM t WHERE a=?", List.of(), null);
        List<QueryParameterApplier.Binding> bindings = List.of(
                new QueryParameterApplier.Binding("a", "sql", null, null, 0));

        assertThatThrownBy(() -> applier.apply(submission, bindings,
                Map.of("a", List.of("1", "2")),
                Map.of("a", new QueryParameterApplier.ParameterMeta("a", "multi-select", false))))
                .hasMessageContaining("标量");
    }

    @Test
    void 相对默认当天与近一周() {
        String config = """
                {"parameters":[
                  {"id":"d","label":"日","type":"date","defaultValue":"$today"},
                  {"id":"r","label":"区间","type":"date-range","defaultValue":"$last7days"}
                ]}
                """;
        Map<String, Object> defaults = applier.defaultParameterValues(config);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        assertThat(defaults.get("d")).isEqualTo(today.toString());
        assertThat(defaults.get("r")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> range = (Map<String, Object>) defaults.get("r");
        assertThat(range.get("start")).isEqualTo(today.minusDays(6).toString());
        assertThat(range.get("end")).isEqualTo(today.toString());
    }

    @Test
    void SQL按名写入namedParameters() {
        QueryService.QuerySubmission submission =
                new QueryService.QuerySubmission(3L,
                        "SELECT * FROM t WHERE a=:channel_id AND b=:start_date", List.of(), null);
        List<QueryParameterApplier.Binding> bindings = List.of(
                new QueryParameterApplier.Binding("ch", "sql", null, null, null, "channel_id"),
                new QueryParameterApplier.Binding("st", "sql", null, null, null, "start_date"));

        QueryService.QuerySubmission applied = applier.apply(submission, bindings,
                Map.of("ch", 93, "st", "2026-08-04"),
                Map.of(
                        "ch", new QueryParameterApplier.ParameterMeta("ch", "number", false),
                        "st", new QueryParameterApplier.ParameterMeta("st", "date", false)));

        assertThat(applied.namedParameters())
                .containsEntry("channel_id", 93)
                .containsEntry("start_date", "2026-08-04");
    }
}
