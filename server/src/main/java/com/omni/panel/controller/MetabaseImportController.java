package com.omni.panel.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.metabase.MetabaseApiClient;
import com.omni.panel.metabase.MetabaseImportService;

/**
 * 管理端 Metabase 仪表盘导入。
 */
@RestController
@RequestMapping("/api/admin/metabase")
@PreAuthorize("hasRole('ADMIN')")
public class MetabaseImportController {
    private final MetabaseImportService importService;

    public MetabaseImportController(MetabaseImportService importService) {
        this.importService = importService;
    }

    /**
     * 列出 Metabase 仪表盘。
     */
    @PostMapping("/dashboards")
    public ApiResponse<List<MetabaseApiClient.DashboardSummary>> list(
            @RequestBody ConnectRequest request) {
        return ApiResponse.ok(importService.listDashboards(request.baseUrl(), request.apiKey()));
    }

    /**
     * 预览导入。
     */
    @PostMapping("/dashboards/preview")
    public ApiResponse<MetabaseImportService.PreviewResult> preview(@RequestBody PreviewBody request) {
        return ApiResponse.ok(importService.preview(new MetabaseImportService.PreviewRequest(
                request.baseUrl(), request.apiKey(), request.metabaseDashboardId())));
    }

    /**
     * 执行导入。
     */
    @PostMapping("/dashboards/import")
    public ApiResponse<MetabaseImportService.ImportResult> importDashboard(@RequestBody ImportBody request) {
        Map<Long, Long> databaseMap = new java.util.LinkedHashMap<>();
        if (request.databaseMap() != null) {
            for (var entry : request.databaseMap().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                try {
                    databaseMap.put(Long.parseLong(entry.getKey().trim()),
                            Long.parseLong(entry.getValue().trim()));
                } catch (NumberFormatException exception) {
                    throw new com.omni.panel.common.BusinessException("数据源映射格式无效");
                }
            }
        }
        Long collectionId = null;
        if (request.collectionId() != null && !request.collectionId().isBlank()) {
            try {
                collectionId = Long.parseLong(request.collectionId().trim());
            } catch (NumberFormatException exception) {
                throw new com.omni.panel.common.BusinessException("集合 ID 无效");
            }
        }
        return ApiResponse.ok(importService.importDashboard(new MetabaseImportService.ImportRequest(
                request.baseUrl(),
                request.apiKey(),
                request.metabaseDashboardId(),
                databaseMap,
                collectionId)));
    }

    public record ConnectRequest(String baseUrl, String apiKey) {
    }

    public record PreviewBody(String baseUrl, String apiKey, long metabaseDashboardId) {
    }

    public record ImportBody(String baseUrl, String apiKey, long metabaseDashboardId,
                             Map<String, String> databaseMap, String collectionId) {
    }
}
