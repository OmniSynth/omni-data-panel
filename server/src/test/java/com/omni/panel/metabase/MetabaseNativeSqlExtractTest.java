package com.omni.panel.metabase;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class MetabaseNativeSqlExtractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 旧版native_query() throws Exception {
        ObjectNode query = mapper.createObjectNode();
        query.put("type", "native");
        query.put("database", 3);
        query.putObject("native").put("query", "SELECT 1");
        var extracted = invoke(query);
        assertThat(extracted).isNotNull();
        assertThat(extracted.sql()).isEqualTo("SELECT 1");
        assertThat(extracted.databaseId()).isEqualTo(3L);
    }

    @Test
    void 新版stages_native() throws Exception {
        ObjectNode query = mapper.createObjectNode();
        query.put("database", 5);
        var stage = query.putArray("stages").addObject();
        stage.put("lib/type", "mbql.stage/native");
        stage.put("native", "SELECT {{x}} FROM t");
        stage.putObject("template-tags").putObject("x").put("name", "x").put("type", "text");
        var extracted = invoke(query);
        assertThat(extracted).isNotNull();
        assertThat(extracted.sql()).isEqualTo("SELECT {{x}} FROM t");
        assertThat(extracted.databaseId()).isEqualTo(5L);
        assertThat(extracted.templateTags()).isNotNull();
    }

    private MetabaseImportService.NativeSql invoke(ObjectNode query) throws Exception {
        var method = MetabaseImportService.class.getDeclaredMethod("extractNativeSql",
                com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        return (MetabaseImportService.NativeSql) method.invoke(null, query);
    }
}
