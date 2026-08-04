package com.omni.panel.search;

import com.omni.panel.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供顶栏全局搜索接口。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService service;

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
