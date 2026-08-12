package com.omni.panel.metabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omni.panel.common.BusinessException;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.entity.ChartEntity;
import com.omni.panel.entity.DashboardEntity;
import com.omni.panel.service.ChartService;
import com.omni.panel.service.DashboardService;
import com.omni.panel.service.DataSourceService;

/**
 * Metabase 仪表盘预览与导入：仅 native SQL；数据源映射到已有 Omni 源。
 */
@Service
public class MetabaseImportService {
    private static final Logger log = LoggerFactory.getLogger(MetabaseImportService.class);

    private final MetabaseApiClient apiClient;
    private final ChartService chartService;
    private final DashboardService dashboardService;
    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;

    public MetabaseImportService(MetabaseApiClient apiClient, ChartService chartService,
                                 DashboardService dashboardService, DataSourceService dataSourceService,
                                 ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.chartService = chartService;
        this.dashboardService = dashboardService;
        this.dataSourceService = dataSourceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 列出远端仪表盘。
     */
    public List<MetabaseApiClient.DashboardSummary> listDashboards(String baseUrl, String apiKey) {
        requireAdmin();
        requireCredential(baseUrl, apiKey);
        return apiClient.listDashboards(baseUrl, apiKey);
    }

    /**
     * 预览导入：依赖的 database_id、可导入/跳过卡片、将生成的参数。
     */
    public PreviewResult preview(PreviewRequest request) {
        requireAdmin();
        requireCredential(request.baseUrl(), request.apiKey());
        if (request.metabaseDashboardId() <= 0) {
            throw new BusinessException("请选择 Metabase 仪表盘");
        }
        JsonNode dashboard = apiClient.getDashboard(request.baseUrl(), request.apiKey(),
                request.metabaseDashboardId());
        ParsedDashboard parsed = parseDashboard(request.baseUrl(), request.apiKey(), dashboard);
        return new PreviewResult(
                parsed.name(),
                List.copyOf(parsed.databaseIds()),
                parsed.importable(),
                parsed.skipped(),
                parsed.parametersPreview());
    }

    /**
     * 执行导入，创建图表与仪表盘。
     */
    @Transactional
    public ImportResult importDashboard(ImportRequest request) {
        requireAdmin();
        requireCredential(request.baseUrl(), request.apiKey());
        if (request.metabaseDashboardId() <= 0) {
            throw new BusinessException("请选择 Metabase 仪表盘");
        }
        if (request.databaseMap() == null || request.databaseMap().isEmpty()) {
            throw new BusinessException("请先映射 Metabase 数据库到 Omni 数据源");
        }
        JsonNode dashboard = apiClient.getDashboard(request.baseUrl(), request.apiKey(),
                request.metabaseDashboardId());
        ParsedDashboard parsed = parseDashboard(request.baseUrl(), request.apiKey(), dashboard);
        for (Long dbId : parsed.databaseIds()) {
            Long mapped = request.databaseMap().get(dbId);
            if (mapped == null) {
                throw new BusinessException("未映射 Metabase 数据库 ID：" + dbId);
            }
            dataSourceService.require(mapped, "READ");
        }
        if (parsed.importable().isEmpty()) {
            throw new BusinessException("没有可导入的原生 SQL 卡片");
        }

        List<String> warnings = new ArrayList<>(parsed.warnings());
        List<Long> chartIds = new ArrayList<>();
        Map<Long, Long> mbCardToChart = new LinkedHashMap<>();

        for (ImportableCard card : parsed.importable()) {
            Long dataSourceId = request.databaseMap().get(card.databaseId());
            MetabaseSqlConverter.Result converted = MetabaseSqlConverter.convert(card.sql());
            warnings.addAll(converted.warnings());
            String queryJson = buildQueryJson(dataSourceId, converted.sql());
            try {
                ChartEntity created = chartService.create(new ChartService.SaveRequest(
                        truncate(card.name(), 100),
                        "Imported from Metabase card #" + card.metabaseCardId(),
                        null,
                        dataSourceId,
                        queryJson,
                        card.chartType(),
                        "{}",
                        request.collectionId()));
                chartIds.add(created.getId());
                mbCardToChart.put(card.metabaseCardId(), created.getId());
            } catch (BusinessException exception) {
                log.warn("Metabase 导入卡片失败 cardId={} name={} message={} originalSql=\n{}\nconvertedSql=\n{}",
                        card.metabaseCardId(), card.name(), exception.getMessage(),
                        card.sql(), converted.sql());
                throw exception;
            }
        }

        String configJson = buildDashboardConfig(parsed.parameters());
        DashboardEntity createdDash = dashboardService.create(
                truncate(parsed.name(), 100),
                "Imported from Metabase dashboard #" + request.metabaseDashboardId(),
                configJson,
                request.collectionId());

        for (ImportableCard card : parsed.importable()) {
            Long chartId = mbCardToChart.get(card.metabaseCardId());
            if (chartId == null) {
                continue;
            }
            String layoutJson = buildLayoutJson(card.col(), card.row(), card.sizeX(), card.sizeY());
            String bindingsJson = buildBindingsJson(card.parameterNames(), parsed.parameters());
            dashboardService.createCard(
                    createdDash.getId(),
                    chartId,
                    truncate(card.name(), 100),
                    layoutJson,
                    bindingsJson,
                    null);
        }

        return new ImportResult(
                createdDash.getId(),
                createdDash.getName(),
                chartIds,
                parsed.skipped(),
                distinct(warnings));
    }

    private ParsedDashboard parseDashboard(String baseUrl, String apiKey, JsonNode dashboard) {
        if (dashboard == null || dashboard.isNull()) {
            throw new BusinessException(404, "Metabase 仪表盘为空");
        }
        String name = text(dashboard, "name");
        if (name.isBlank()) {
            name = "Imported Dashboard";
        }
        List<OmniParameter> parameters = parseParameters(dashboard.get("parameters"));
        Set<Long> databaseIds = new LinkedHashSet<>();
        List<ImportableCard> importable = new ArrayList<>();
        List<SkippedCard> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        JsonNode dashcards = dashboard.get("dashcards");
        if (dashcards == null || !dashcards.isArray()) {
            dashcards = dashboard.get("ordered_cards");
        }
        if (dashcards == null || !dashcards.isArray()) {
            return new ParsedDashboard(name, databaseIds, importable, skipped, parameters,
                    parametersPreview(parameters), warnings);
        }

        for (JsonNode dashcard : dashcards) {
            if (dashcard == null || dashcard.isNull()) {
                continue;
            }
            // 跳过虚拟卡（文本/标题）
            if (dashcard.has("visualization_settings")
                    && !dashcard.has("card_id")
                    && (dashcard.get("card") == null || dashcard.get("card").isNull())) {
                continue;
            }
            Long cardId = longOrNull(dashcard.get("card_id"));
            JsonNode cardNode = dashcard.get("card");
            if (cardId == null && cardNode != null && !cardNode.isNull()) {
                cardId = longOrNull(cardNode.get("id"));
            }
            // 仪表盘嵌入的 card 常不完整；有 card_id 一律拉详情
            if (cardId != null) {
                try {
                    cardNode = apiClient.getCard(baseUrl, apiKey, cardId);
                } catch (BusinessException ex) {
                    skipped.add(new SkippedCard(cardId, "card#" + cardId, ex.getMessage()));
                    continue;
                }
            }
            if (cardNode == null || cardNode.isNull() || cardNode.isEmpty()) {
                skipped.add(new SkippedCard(cardId, "unknown", "缺少卡片定义"));
                continue;
            }
            if (cardId == null) {
                cardId = longOrNull(cardNode.get("id"));
            }
            String cardName = text(cardNode, "name");
            if (cardName.isBlank()) {
                cardName = "Card #" + (cardId == null ? "?" : cardId);
            }
            JsonNode query = resolveDatasetQuery(cardNode);
            if (query == null || query.isNull()) {
                skipped.add(new SkippedCard(cardId, cardName, "无 dataset_query"));
                continue;
            }
            NativeSql extracted = extractNativeSql(query);
            if (extracted == null || extracted.sql().isBlank()) {
                skipped.add(new SkippedCard(cardId, cardName, skipReasonForNonNative(query)));
                continue;
            }
            if (hasFieldFilterTags(extracted.templateTags())) {
                skipped.add(new SkippedCard(cardId, cardName, "含 Field Filter，第一版不支持"));
                continue;
            }
            String sql;
            try {
                sql = expandNestedCardSql(baseUrl, apiKey, extracted.sql(), new LinkedHashSet<>(), 0);
            } catch (BusinessException ex) {
                skipped.add(new SkippedCard(cardId, cardName, ex.getMessage()));
                continue;
            }
            MetabaseSqlConverter.Result probe = MetabaseSqlConverter.convert(sql);
            if (probe.sql().contains("{{")) {
                String reason = probe.warnings().isEmpty()
                        ? "含无法转换的模板标签"
                        : probe.warnings().getFirst();
                skipped.add(new SkippedCard(cardId, cardName, reason));
                continue;
            }
            Long databaseId = extracted.databaseId() != null
                    ? extracted.databaseId()
                    : longOrNull(query.get("database"));
            if (databaseId == null) {
                skipped.add(new SkippedCard(cardId, cardName, "缺少 database_id"));
                continue;
            }
            databaseIds.add(databaseId);
            Set<String> paramNames = extractMappedParamNames(dashcard.get("parameter_mappings"));
            paramNames.addAll(extractTemplateTagNames(extracted.templateTags()));
            paramNames.addAll(probe.defaults().keySet());
            importable.add(new ImportableCard(
                    cardId,
                    cardName,
                    databaseId,
                    sql,
                    mapDisplay(text(cardNode, "display")),
                    intOr(dashcard.get("col"), 0),
                    intOr(dashcard.get("row"), 0),
                    intOr(dashcard.get("size_x"), 6),
                    intOr(dashcard.get("size_y"), 4),
                    List.copyOf(paramNames)));
        }
        return new ParsedDashboard(name, databaseIds, importable, skipped, parameters,
                parametersPreview(parameters), warnings);
    }

    private static final int MAX_CARD_REF_DEPTH = 5;

    /**
     * 将 {@code {{#id}}} 展开为 {@code (子查询)}，与 Metabase 运行时行为一致。
     */
    private String expandNestedCardSql(String baseUrl, String apiKey, String sql,
                                       Set<Long> stack, int depth) {
        MetabaseSqlConverter.CardExpandResult expanded = MetabaseSqlConverter.replaceCardRefs(sql, refId -> {
            if (depth >= MAX_CARD_REF_DEPTH) {
                throw new BusinessException("嵌套卡片层级过深（>" + MAX_CARD_REF_DEPTH + "）");
            }
            if (!stack.add(refId)) {
                throw new BusinessException("嵌套卡片循环引用 #" + refId);
            }
            try {
                JsonNode cardNode = apiClient.getCard(baseUrl, apiKey, refId);
                JsonNode query = resolveDatasetQuery(cardNode);
                NativeSql nested = extractNativeSql(query);
                if (nested == null || nested.sql().isBlank()) {
                    throw new BusinessException("嵌套卡片 #" + refId + " 非原生 SQL，无法展开");
                }
                if (hasFieldFilterTags(nested.templateTags())) {
                    throw new BusinessException("嵌套卡片 #" + refId + " 含 Field Filter，无法展开");
                }
                String inner = expandNestedCardSql(baseUrl, apiKey, nested.sql(), stack, depth + 1);
                return "(" + MetabaseSqlConverter.collapseExcessBlankLines(
                        MetabaseSqlConverter.removeSemicolonsOutsideStrings(inner)).stripTrailing() + ")";
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BusinessException("拉取嵌套卡片 #" + refId + " 失败：" + ex.getMessage());
            } finally {
                stack.remove(refId);
            }
        });
        if (expanded.error() != null) {
            throw new BusinessException(expanded.error());
        }
        return expanded.sql();
    }

    /** 解析 dataset_query：兼容对象与 JSON 字符串。 */
    private JsonNode resolveDatasetQuery(JsonNode cardNode) {
        JsonNode query = cardNode.get("dataset_query");
        if (query == null || query.isNull()) {
            return null;
        }
        if (query.isTextual()) {
            try {
                return objectMapper.readTree(query.asText());
            } catch (Exception exception) {
                return null;
            }
        }
        return query;
    }

    /**
     * 从旧版 {@code native.query} 或新版 {@code stages[mbql.stage/native]} 提取 SQL。
     */
    private static NativeSql extractNativeSql(JsonNode query) {
        if (query == null || query.isNull()) {
            return null;
        }
        Long databaseId = longOrNull(query.get("database"));
        JsonNode nativeNode = query.get("native");
        if (nativeNode != null && !nativeNode.isNull()) {
            if (nativeNode.isTextual()) {
                String sql = nativeNode.asText("").trim();
                if (!sql.isBlank()) {
                    return new NativeSql(sql, null, databaseId);
                }
            } else if (nativeNode.isObject()) {
                String sql = text(nativeNode, "query");
                if (!sql.isBlank()) {
                    return new NativeSql(sql, nativeNode.get("template-tags"), databaseId);
                }
            }
        }
        JsonNode stages = query.get("stages");
        if (stages != null && stages.isArray()) {
            for (JsonNode stage : stages) {
                if (stage == null || stage.isNull()) {
                    continue;
                }
                String libType = text(stage, "lib/type").toLowerCase(Locale.ROOT);
                JsonNode stageNative = stage.get("native");
                if (stageNative == null || stageNative.isNull()) {
                    continue;
                }
                String sql = stageNative.isTextual() ? stageNative.asText("") : text(stageNative, "query");
                if (sql.isBlank()) {
                    continue;
                }
                if (libType.contains("native") || libType.isBlank()) {
                    JsonNode tags = stage.get("template-tags");
                    if (tags == null && stageNative.isObject()) {
                        tags = stageNative.get("template-tags");
                    }
                    return new NativeSql(sql.trim(), tags, databaseId);
                }
            }
        }
        return null;
    }

    private static String skipReasonForNonNative(JsonNode query) {
        String type = text(query, "type").toLowerCase(Locale.ROOT).trim();
        boolean hasStages = query.has("stages") && query.get("stages").isArray()
                && !query.get("stages").isEmpty();
        if ("query".equals(type)) {
            return "非原生 SQL（GUI/模型问题）";
        }
        if (hasStages) {
            return "非原生 SQL（MBQL stages，无 mbql.stage/native）";
        }
        if (type.isBlank()) {
            return "非原生 SQL（无 native.query / stages.native）";
        }
        return "非原生 SQL（type=" + type + "）";
    }

    record NativeSql(String sql, JsonNode templateTags, Long databaseId) {
    }

    private static boolean hasFieldFilterTags(JsonNode tags) {
        if (tags == null || !tags.isObject()) {
            return false;
        }
        var fields = tags.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode tag = entry.getValue();
            String type = text(tag, "type").toLowerCase(Locale.ROOT);
            if ("dimension".equals(type) || "temporal-unit".equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> extractTemplateTagNames(JsonNode tags) {
        Set<String> names = new LinkedHashSet<>();
        if (tags == null || !tags.isObject()) {
            return names;
        }
        var fields = tags.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode tag = entry.getValue();
            String name = text(tag, "name");
            if (name.isBlank()) {
                name = entry.getKey();
            }
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private static Set<String> extractMappedParamNames(JsonNode mappings) {
        Set<String> names = new LinkedHashSet<>();
        if (mappings == null || !mappings.isArray()) {
            return names;
        }
        for (JsonNode mapping : mappings) {
            JsonNode target = mapping.get("target");
            if (target == null || !target.isArray() || target.size() < 2) {
                continue;
            }
            // ["variable", ["template-tag", "name"]] or ["dimension", ...]
            JsonNode second = target.get(1);
            if (second != null && second.isArray() && second.size() >= 2
                    && "template-tag".equalsIgnoreCase(second.get(0).asText(""))) {
                String name = second.get(1).asText("");
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private List<OmniParameter> parseParameters(JsonNode parameters) {
        List<OmniParameter> result = new ArrayList<>();
        if (parameters == null || !parameters.isArray()) {
            return result;
        }
        for (JsonNode node : parameters) {
            if (node == null || node.isNull()) {
                continue;
            }
            String id = text(node, "id");
            if (id.isBlank()) {
                id = text(node, "slug");
            }
            if (id.isBlank()) {
                continue;
            }
            String label = text(node, "name");
            if (label.isBlank()) {
                label = text(node, "slug");
            }
            if (label.isBlank()) {
                label = id;
            }
            String slug = text(node, "slug");
            String type = mapParameterType(text(node, "type"));
            Object defaultValue = null;
            JsonNode def = node.get("default");
            if (def != null && !def.isNull()) {
                if (def.isTextual()) {
                    defaultValue = def.asText();
                } else if (def.isNumber()) {
                    defaultValue = def.numberValue();
                } else if (def.isArray() && !def.isEmpty()) {
                    defaultValue = def.get(0).asText();
                } else {
                    defaultValue = def.asText(null);
                }
            }
            String bindName = !slug.isBlank() ? slug : id;
            result.add(new OmniParameter(id, label, type, defaultValue, bindName));
        }
        return result;
    }

    private static String mapParameterType(String mbType) {
        String type = mbType == null ? "" : mbType.toLowerCase(Locale.ROOT);
        if (type.startsWith("date")) {
            if (type.contains("range")) {
                return "date-range";
            }
            return "date";
        }
        if (type.startsWith("number")) {
            return "number";
        }
        if (type.contains("category") || type.contains("/=")) {
            return "select";
        }
        return "text";
    }

    private static String mapDisplay(String display) {
        String d = display == null ? "" : display.toLowerCase(Locale.ROOT);
        return switch (d) {
            case "bar", "row" -> "bar".equals(d) ? "bar" : "hbar";
            case "line" -> "line";
            case "area" -> "area";
            case "pie", "donut" -> "pie";
            case "scalar", "smartscalar", "gauge" -> "kpi";
            case "combo", "waterfall" -> "combo";
            case "funnel" -> "funnel";
            case "map", "pin_map", "state" -> "map";
            case "scatter", "bubble" -> "scatter";
            case "table", "pivot", "list" -> "table";
            default -> "table";
        };
    }

    private String buildQueryJson(long dataSourceId, String sql) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("sourceId", dataSourceId);
            root.put("sql", sql);
            root.putNull("parameters");
            root.putNull("namedParameters");
            root.putNull("query");
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new BusinessException("构建查询 JSON 失败");
        }
    }

    private String buildDashboardConfig(List<OmniParameter> parameters) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode params = root.putArray("parameters");
            for (OmniParameter parameter : parameters) {
                ObjectNode node = params.addObject();
                node.put("id", parameter.id());
                node.put("label", parameter.label());
                node.put("type", parameter.type());
                if (parameter.defaultValue() != null) {
                    node.set("defaultValue", objectMapper.valueToTree(parameter.defaultValue()));
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new BusinessException("构建仪表盘配置失败");
        }
    }

    private String buildBindingsJson(List<String> parameterNames, List<OmniParameter> parameters) {
        try {
            ArrayNode arr = objectMapper.createArrayNode();
            Map<String, OmniParameter> byBind = new LinkedHashMap<>();
            for (OmniParameter parameter : parameters) {
                byBind.put(parameter.bindName(), parameter);
                byBind.putIfAbsent(parameter.id(), parameter);
            }
            for (String name : parameterNames) {
                OmniParameter matched = byBind.get(name);
                ObjectNode binding = arr.addObject();
                binding.put("parameterId", matched != null ? matched.id() : name);
                binding.put("mode", "sql");
                binding.put("parameterName", name);
            }
            return objectMapper.writeValueAsString(arr);
        } catch (Exception exception) {
            throw new BusinessException("构建参数绑定失败");
        }
    }

    private static String buildLayoutJson(int col, int row, int sizeX, int sizeY) {
        int x = Math.max(0, Math.min(11, col / 2));
        int w = Math.max(1, Math.min(12 - x, (int) Math.ceil(sizeX / 2.0)));
        int y = Math.max(0, row);
        int h = Math.max(2, sizeY);
        return "{\"x\":" + x + ",\"y\":" + y + ",\"w\":" + w + ",\"h\":" + h + "}";
    }

    private static List<ParameterPreview> parametersPreview(List<OmniParameter> parameters) {
        List<ParameterPreview> list = new ArrayList<>();
        for (OmniParameter parameter : parameters) {
            list.add(new ParameterPreview(parameter.id(), parameter.label(), parameter.type(),
                    parameter.bindName()));
        }
        return list;
    }

    private static void requireAdmin() {
        if (!AuthenticatedUser.current().admin()) {
            throw new BusinessException(403, "仅管理员可导入 Metabase 仪表盘");
        }
    }

    private static void requireCredential(String baseUrl, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException("Metabase 地址不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("Metabase API Key 不能为空");
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static Long longOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private static int intOr(JsonNode node, int fallback) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return fallback;
        }
        return node.asInt(fallback);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    public record PreviewRequest(String baseUrl, String apiKey, long metabaseDashboardId) {
    }

    public record ImportRequest(String baseUrl, String apiKey, long metabaseDashboardId,
                                Map<Long, Long> databaseMap, Long collectionId) {
    }

    public record PreviewResult(String name, List<Long> databaseIds, List<ImportableCard> importableCards,
                                List<SkippedCard> skippedCards, List<ParameterPreview> parameters) {
    }

    public record ImportResult(long dashboardId, String dashboardName, List<Long> chartIds,
                               List<SkippedCard> skippedCards, List<String> warnings) {
    }

    public record ParameterPreview(String id, String label, String type, String bindName) {
    }

    public record SkippedCard(Long metabaseCardId, String name, String reason) {
    }

    public record ImportableCard(Long metabaseCardId, String name, long databaseId, String sql,
                                 String chartType, int col, int row, int sizeX, int sizeY,
                                 List<String> parameterNames) {
    }

    private record OmniParameter(String id, String label, String type, Object defaultValue, String bindName) {
    }

    private record ParsedDashboard(String name, Set<Long> databaseIds, List<ImportableCard> importable,
                                   List<SkippedCard> skipped, List<OmniParameter> parameters,
                                   List<ParameterPreview> parametersPreview, List<String> warnings) {
    }
}
