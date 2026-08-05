package com.omni.panel.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.omni.panel.common.BusinessException;

class QueryCompilerTest {
    private final QueryCompiler compiler = new QueryCompiler();
    private final QueryCompiler.DatasetDefinition dataset = new QueryCompiler.DatasetDefinition(
            "sales", "orders", List.of(
            new QueryCompiler.FieldDefinition("地区", "region", "DIMENSION", null),
            new QueryCompiler.FieldDefinition("销售额", "amount", "METRIC", "SUM")
    ));

    @Test
    @DisplayName("查询值始终使用参数占位符")
    void parameterizesValues() {
        QueryRequest request = new QueryRequest(1L, List.of("地区"), List.of("销售额"), null,
                new QueryRequest.FilterNode(null, "地区", "EQ", "华东' OR 1=1", null),
                List.of(new QueryRequest.SortItem("销售额", "DESC")), 100);

        QueryCompiler.CompiledQuery compiled = compiler.compile(request, dataset, Set.of(), List.of());

        assertFalse(compiled.sql().contains("华东"));
        assertEquals(List.of("华东' OR 1=1", 100), compiled.parameters());
        assertEquals("SELECT `region` AS `地区`, SUM(`amount`) AS `销售额` FROM `sales`.`orders` "
                + "WHERE `region` = ? GROUP BY `region` ORDER BY `销售额` DESC LIMIT ?", compiled.sql());
    }

    @Test
    @DisplayName("拒绝白名单外字段和无权字段")
    void rejectsUnknownAndDeniedFields() {
        QueryRequest unknown = new QueryRequest(1L, List.of("不存在"), List.of(), null, null, null, 10);
        QueryRequest denied = new QueryRequest(1L, List.of("地区"), List.of(), null, null, null, 10);

        assertThrows(BusinessException.class,
                () -> compiler.compile(unknown, dataset, Set.of(), List.of()));
        assertThrows(BusinessException.class,
                () -> compiler.compile(denied, dataset, Set.of("地区"), List.of()));
    }

    @Test
    @DisplayName("编译字段去重查询")
    void compilesDistinct() {
        QueryCompiler.CompiledQuery compiled =
                compiler.compileDistinct(dataset, "地区", 200, Set.of(), null);

        assertEquals("SELECT DISTINCT `region` AS `地区` FROM `sales`.`orders` "
                + "WHERE `region` IS NOT NULL ORDER BY `region` LIMIT ?", compiled.sql());
        assertEquals(List.of(200), compiled.parameters());
    }

    @Test
    @DisplayName("去重查询拒绝无权字段")
    void distinctRejectsDeniedField() {
        assertThrows(BusinessException.class,
                () -> compiler.compileDistinct(dataset, "地区", 50, Set.of("地区"), null));
    }
}
