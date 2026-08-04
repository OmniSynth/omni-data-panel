package com.omni.panel.controller;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.entity.PublicLinkEntity;
import com.omni.panel.service.PublicLinkService;

/**
 * 提供公开分享链接的认证管理接口。
 */
@RestController
@RequestMapping("/api/public-links")
public class PublicLinkController {
    private final PublicLinkService service;

    public PublicLinkController(PublicLinkService service) {
        this.service = service;
    }

    /**
     * 列出可管理的公开链接。
     *
     * @return 链接列表
     */
    @GetMapping
    public ApiResponse<List<PublicLinkEntity>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * 创建公开链接。
     *
     * @param request 创建参数
     * @return 已创建链接
     */
    @PostMapping
    public ApiResponse<PublicLinkEntity> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(request.resourceType(), request.resourceId()));
    }

    /**
     * 撤销公开链接。
     *
     * @param id 链接标识
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> revoke(@PathVariable long id) {
        service.revoke(id);
        return ApiResponse.ok();
    }

    /**
     * 创建公开链接请求。
     */
    public record CreateRequest(@NotBlank String resourceType, @NotNull Long resourceId) {
    }
}
