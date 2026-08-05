package com.omni.panel.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.omni.panel.controller.AuthController;
import com.omni.panel.controller.ChartController;
import com.omni.panel.controller.DataSourceController;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.query.JdbcQueryExecutor;
import com.omni.panel.query.QueryRequest;
import com.omni.panel.query.QueryStateStore;
import com.omni.panel.service.DashboardRenderService;
import com.omni.panel.service.QueryService;

class ApiContractJsonTest {
    private static final long LARGE_ID = 9_007_199_254_740_993L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 登录结果和用户字段名称保持稳定() {
        JsonNode login = objectMapper.valueToTree(new AuthController.LoginResult("jwt", "Bearer"));
        JsonNode user = objectMapper.valueToTree(
                new AuthController.UserView(
                        1, "admin", "系统管理员", "admin@example.com",
                        List.of("ADMIN"), true, List.of("data-source:manage")));

        assertThat(login.path("accessToken").asText()).isEqualTo("jwt");
        assertThat(login.has("token")).isFalse();
        assertThat(user.path("admin").asBoolean()).isTrue();
        assertThat(user.path("email").asText()).isEqualTo("admin@example.com");
        assertThat(user.path("permissions").get(0).asText()).isEqualTo("data-source:manage");
    }

    @Test
    void 数据源视图的大整数标识序列化为精确字符串() {
        JsonNode json = objectMapper.valueToTree(
                new DataSourceController.View(LARGE_ID, "主库", "localhost", 3306, "db",
                        "jdbc:mysql://localhost/db", "MYSQL", "reader", "ACTIVE", LARGE_ID));

        assertThat(json.path("id").isTextual()).isTrue();
        assertThat(json.path("id").asText()).isEqualTo("9007199254740993");
        assertThat(json.path("ownerId").asText()).isEqualTo("9007199254740993");
        assertThat(json.path("dialect").asText()).isEqualTo("MYSQL");
    }

    @Test
    void 通用资源实体的大整数标识序列化为精确字符串() {
        ChartEntity chart = new ChartEntity();
        chart.setId(LARGE_ID);
        chart.setDatasetId(LARGE_ID);
        chart.setOwnerId(LARGE_ID);

        JsonNode json = objectMapper.valueToTree(chart);

        assertThat(json.path("id").isTextual()).isTrue();
        assertThat(json.path("id").asText()).isEqualTo("9007199254740993");
        assertThat(json.path("datasetId").isTextual()).isTrue();
        assertThat(json.path("datasetId").asText()).isEqualTo("9007199254740993");
        assertThat(json.path("ownerId").isTextual()).isTrue();
        assertThat(json.path("ownerId").asText()).isEqualTo("9007199254740993");
    }

    @Test
    void 查询快照的大整数关联标识序列化为精确字符串() {
        JsonNode json = objectMapper.valueToTree(
                new QueryStateStore.QuerySnapshot("query-1", LARGE_ID, LARGE_ID, "QUEUED", null, null, 1L, null));

        assertThat(json.path("userId").isTextual()).isTrue();
        assertThat(json.path("userId").asText()).isEqualTo("9007199254740993");
        assertThat(json.path("sourceId").isTextual()).isTrue();
        assertThat(json.path("sourceId").asText()).isEqualTo("9007199254740993");
    }

    @Test
    void 十进制字符串标识可反序列化为请求Long字段() throws Exception {
        ChartController.SaveRequest request = objectMapper.readValue("""
                {
                  "name": "销售图表",
                  "datasetId": "9007199254740993",
                  "queryJson": "{}",
                  "chartType": "bar",
                  "configJson": "{}"
                }
                """, ChartController.SaveRequest.class);

        assertThat(request.datasetId()).isEqualTo(LARGE_ID);
    }

    @Test
    void 修改密码请求字段名称保持稳定() {
        JsonNode request = objectMapper.valueToTree(
                new AuthController.ChangePasswordRequest("当前密码12345", "新密码123456"));

        assertThat(request.path("currentPassword").asText()).isEqualTo("当前密码12345");
        assertThat(request.path("newPassword").asText()).isEqualTo("新密码123456");
        assertThat(request.size()).isEqualTo(2);
    }

    @Test
    void 查询提交字段匹配语义查询契约() {
        QueryRequest query = new QueryRequest(2L, List.of("region"), List.of("sales"), null,
                null, List.of(new QueryRequest.SortItem("sales", "DESC")), 100);
        JsonNode json = objectMapper.valueToTree(new QueryService.QuerySubmission(null, null, null, query));

        assertThat(json.path("query").path("datasetId").asLong()).isEqualTo(2L);
        assertThat(json.path("query").path("metrics").get(0).asText()).isEqualTo("sales");
        assertThat(json.path("query").has("measures")).isFalse();
        assertThat(json.has("sourceId")).isTrue();
        assertThat(json.has("parameters")).isTrue();
    }

    @Test
    void 查询结果按列名序列化为对象数组() {
        var row = new LinkedHashMap<String, Object>();
        row.put("name", "华东");
        row.put("name_2", 12);
        JsonNode json = objectMapper.valueToTree(
                new JdbcQueryExecutor.QueryResult(List.of("name", "name_2"), List.of(row)));

        assertThat(json.path("columns").get(1).asText()).isEqualTo("name_2");
        assertThat(json.path("rows").get(0).path("name").asText()).isEqualTo("华东");
        assertThat(json.path("rows").get(0).path("name_2").asInt()).isEqualTo(12);
        assertThat(json.path("rows").get(0).isObject()).isTrue();
    }

    @Test
    void 仪表盘渲染结果不暴露查询与数据源敏感字段() {
        var card = new DashboardRenderService.RenderedCard(
                2L, "销售额", "bar", "{}", "{}", "[]", null, null, null);
        JsonNode json = objectMapper.valueToTree(
                new DashboardRenderService.RenderedDashboard(1L, "经营看板", "{}", "READ", List.of(card)));
        String serialized = json.toString();

        assertThat(serialized).doesNotContain(
                "queryJson", "sourceId", "dataSourceId", "datasetId", "jdbcUrl", "password");
        assertThat(json.path("accessLevel").asText()).isEqualTo("READ");
    }
}
