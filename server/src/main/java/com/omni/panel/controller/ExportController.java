package com.omni.panel.controller;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.ClientRequestInfo;
import com.omni.panel.entity.ExportTaskEntity;
import com.omni.panel.service.ExportService;

/**
 * 提供查询结果的同步下载、异步导出提交、状态查询和文件下载接口。
 */
@RestController
@RequestMapping("/api/exports")
public class ExportController {
    private final ExportService service;

    /**
     * 注入导出业务服务。
     */
    public ExportController(ExportService service) {
        this.service = service;
    }

    /**
     * 将已成功查询的结果同步生成并作为附件返回。
     */
    @GetMapping("/queries/{queryId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('export:execute')")
    public ResponseEntity<byte[]> synchronous(@PathVariable String queryId,
                                              @RequestParam(defaultValue = "CSV") String format,
                                              HttpServletRequest request) {
        byte[] content = service.synchronous(queryId, format, ClientRequestInfo.from(request));
        String extension = format.toLowerCase();
        MediaType mediaType = "xlsx".equals(extension)
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");
        String disposition = ContentDisposition.attachment()
                .filename("查询结果." + extension, StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition).body(content);
    }

    /**
     * 提交基于请求体的异步导出任务。
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('export:execute')")
    public ApiResponse<TaskResult> submit(@Valid @RequestBody ExportRequest requestBody,
                                          HttpServletRequest request) {
        return ApiResponse.ok(new TaskResult(
                service.asynchronous(requestBody.queryId(), requestBody.format(), ClientRequestInfo.from(request))));
    }

    /**
     * 为指定查询提交异步导出任务。
     */
    @PostMapping("/queries/{queryId}/async")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('export:execute')")
    public ApiResponse<TaskResult> asynchronous(@PathVariable String queryId,
                                                @RequestParam(defaultValue = "XLSX") String format,
                                                HttpServletRequest request) {
        return ApiResponse.ok(new TaskResult(
                service.asynchronous(queryId, format, ClientRequestInfo.from(request))));
    }

    /**
     * 查询本人创建或管理员可见的异步导出任务状态。
     */
    @GetMapping("/{taskId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('export:execute')")
    public ApiResponse<ExportTaskEntity> status(@PathVariable String taskId) {
        return ApiResponse.ok(service.get(taskId));
    }

    /**
     * 下载本人创建或管理员可见且已成功完成的异步导出文件。
     */
    @GetMapping("/{taskId}/download")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('export:execute')")
    public ResponseEntity<InputStreamResource> download(@PathVariable String taskId) {
        ExportService.Download download = service.download(taskId);
        MediaType mediaType = "XLSX".equals(download.format())
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");
        String disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(new InputStreamResource(download.stream()));
    }

    /**
     * 异步导出提交请求。
     */
    public record ExportRequest(@NotBlank String queryId, @NotBlank String format) {
    }

    /**
     * 异步导出提交结果。
     */
    public record TaskResult(String taskId) {
    }
}
