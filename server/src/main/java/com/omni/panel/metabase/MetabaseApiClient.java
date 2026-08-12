package com.omni.panel.metabase;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.omni.panel.common.BusinessException;

/**
 * Metabase REST 客户端（API Key）；连接信息仅用于当次请求。
 */
@Component
public class MetabaseApiClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MetabaseApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 列出仪表盘（id / name）。
     *
     * @param baseUrl Metabase 根地址
     * @param apiKey  API Key
     * @return 仪表盘摘要
     */
    public List<DashboardSummary> listDashboards(String baseUrl, String apiKey) {
        JsonNode root = getJson(baseUrl, apiKey, "/api/dashboard");
        List<DashboardSummary> items = new ArrayList<>();
        if (root == null || !root.isArray()) {
            return items;
        }
        for (JsonNode node : root) {
            if (node == null || !node.has("id")) {
                continue;
            }
            long id = node.get("id").asLong();
            String name = text(node, "name");
            if (name.isBlank()) {
                name = "Dashboard #" + id;
            }
            items.add(new DashboardSummary(id, name));
        }
        return items;
    }

    /**
     * 获取仪表盘详情 JSON。
     *
     * @param baseUrl     Metabase 根地址
     * @param apiKey      API Key
     * @param dashboardId 仪表盘 ID
     * @return 详情节点
     */
    public JsonNode getDashboard(String baseUrl, String apiKey, long dashboardId) {
        return getJson(baseUrl, apiKey, "/api/dashboard/" + dashboardId);
    }

    /**
     * 获取问题卡片详情。
     *
     * @param baseUrl Metabase 根地址
     * @param apiKey  API Key
     * @param cardId  卡片 ID
     * @return 卡片节点
     */
    public JsonNode getCard(String baseUrl, String apiKey, long cardId) {
        return getJson(baseUrl, apiKey, "/api/card/" + cardId);
    }

    private JsonNode getJson(String baseUrl, String apiKey, String path) {
        URI uri = URI.create(normalizeBase(baseUrl) + path);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .header("X-Api-Key", apiKey.trim())
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code == 401 || code == 403) {
                throw new BusinessException(401, "Metabase 认证失败，请检查 API Key");
            }
            if (code == 404) {
                throw new BusinessException(404, "Metabase 资源不存在：" + path);
            }
            if (code < 200 || code >= 300) {
                throw new BusinessException(502, "Metabase 请求失败 HTTP " + code + "：" + path);
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                return objectMapper.nullNode();
            }
            return objectMapper.readTree(body);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(502, "连接 Metabase 失败：" + rootMessage(exception));
        }
    }

    private static String normalizeBase(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException("Metabase 地址不能为空");
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new BusinessException("Metabase 地址须以 http:// 或 https:// 开头");
        }
        return trimmed;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /**
     * 仪表盘列表项。
     *
     * @param id   Metabase 仪表盘 ID
     * @param name 名称
     */
    public record DashboardSummary(long id, String name) {
    }
}
