package com.omni.panel.collection;

import com.omni.panel.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供集合树、内容列表与迁移接口。
 */
@RestController
@RequestMapping("/api/collections")
public class CollectionController {
    private final CollectionService service;

    public CollectionController(CollectionService service) {
        this.service = service;
    }

    /**
     * 查询集合树。
     *
     * @return 根节点列表
     */
    @GetMapping
    public ApiResponse<List<CollectionService.CollectionNode>> tree() {
        return ApiResponse.ok(service.tree());
    }

    /**
     * 创建非个人集合。
     *
     * @param request 创建参数
     * @return 已创建集合
     */
    @PostMapping
    public ApiResponse<CollectionEntity> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(request.name(), request.description(), request.parentId()));
    }

    /**
     * 更新集合。
     *
     * @param id 集合标识
     * @param request 更新参数
     * @return 已更新集合
     */
    @PutMapping("/{id}")
    public ApiResponse<CollectionEntity> update(@PathVariable long id,
                                                @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(service.update(id, request.name(), request.description()));
    }

    /**
     * 删除空集合。
     *
     * @param id 集合标识
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 查询集合内容。
     *
     * @param id 集合标识
     * @return 内容条目
     */
    @GetMapping("/{id}/items")
    public ApiResponse<List<CollectionService.CollectionItem>> items(@PathVariable long id) {
        return ApiResponse.ok(service.items(id));
    }

    /**
     * 迁移资源到目标集合。
     *
     * @param request 迁移参数
     * @return 空成功响应
     */
    @PutMapping("/move")
    public ApiResponse<Void> move(@Valid @RequestBody MoveRequest request) {
        service.move(request.resourceType(), request.resourceId(), request.collectionId());
        return ApiResponse.ok();
    }

    /**
     * 集合创建请求。
     */
    public record CreateRequest(@NotBlank String name, String description, Long parentId) {}

    /**
     * 集合更新请求。
     */
    public record UpdateRequest(@NotBlank String name, String description) {}

    /**
     * 资源迁移请求。
     */
    public record MoveRequest(@NotBlank String resourceType, @NotNull Long resourceId,
                              @NotNull Long collectionId) {}
}
