package com.omni.panel.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.service.SearchService;

/**
 * 提供顶栏全局搜索接口。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService service;

    /**
     * 注入全局搜索业务服务。
     *
     * @param service 搜索服务
     */
    public SearchController(SearchService service) {
        this.service = service;
    }

    /**
     * 按名称模糊搜索资源。
     *
     * @param q 关键字
     * @return 搜索命中列表
     */
    @GetMapping
    public ApiResponse<List<SearchService.SearchHit>> search(@RequestParam String q) {
        return ApiResponse.ok(service.search(q));
    }
}
