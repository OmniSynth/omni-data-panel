package com.omni.panel.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.service.TrashItem;
import com.omni.panel.service.TrashService;

/**
 * 提供废纸篓列表、恢复与永久删除接口。
 */
@RestController
@RequestMapping("/api/trash")
public class TrashController {
    private final TrashService service;

    /**
     * 注入废纸篓业务服务。
     *
     * @param service 废纸篓服务
     */
    public TrashController(TrashService service) {
        this.service = service;
    }

    /**
     * 查询废纸篓。
     *
     * @return 已软删资源列表
     */
    @GetMapping
    public ApiResponse<List<TrashItem>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * 恢复资源。
     *
     * @param request 资源定位
     * @return 空成功响应
     */
    @PostMapping("/restore")
    public ApiResponse<Void> restore(@Valid @RequestBody ResourceRequest request) {
        service.restore(request.resourceType(), request.resourceId());
        return ApiResponse.ok();
    }

    /**
     * 永久删除资源。
     *
     * @param request 资源定位
     * @return 空成功响应
     */
    @DeleteMapping
    public ApiResponse<Void> purge(@Valid @RequestBody ResourceRequest request) {
        service.purge(request.resourceType(), request.resourceId());
        return ApiResponse.ok();
    }

    /**
     * 废纸篓资源定位请求。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     */
    public record ResourceRequest(@NotBlank String resourceType, @NotNull Long resourceId) {
    }
}
