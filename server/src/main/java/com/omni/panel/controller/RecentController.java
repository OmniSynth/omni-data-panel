package com.omni.panel.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.config.AuthenticatedUser;
import com.omni.panel.service.RecentService;

/**
 * 提供首页续看所需的最近访问接口。
 */
@RestController
@RequestMapping("/api/recents")
public class RecentController {
    private final RecentService service;

    public RecentController(RecentService service) {
        this.service = service;
    }

    /**
     * 查询当前用户最近访问资源。
     *
     * @param limit 条数上限，默认 20
     * @return 最近访问列表
     */
    @GetMapping
    public ApiResponse<List<RecentService.RecentView>> list(
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.list(AuthenticatedUser.current().id(), limit));
    }
}
