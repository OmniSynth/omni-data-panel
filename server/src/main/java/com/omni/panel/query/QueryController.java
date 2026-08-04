package com.omni.panel.query;

import com.omni.panel.common.ApiResponse;
import com.omni.panel.common.ClientRequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 提供查询提交、状态查询和取消接口。
 */
@RestController
@RequestMapping("/api/queries")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('query:execute')")
public class QueryController {
    private final QueryService service;

    public QueryController(QueryService service) {
        this.service = service;
    }

    /**
     * 提交原生 SQL 或语义查询并启动异步执行。
     *
     * @param submission 查询提交内容
     * @param request 当前 HTTP 请求，用于记录 IP 与浏览器
     * @return 包含查询任务标识的响应
     */
    @PostMapping
    public ApiResponse<SubmitResult> submit(@Valid @RequestBody QueryService.QuerySubmission submission,
                                            HttpServletRequest request) {
        return ApiResponse.ok(new SubmitResult(service.submit(submission, ClientRequestInfo.from(request))));
    }

    /**
     * 获取当前用户有权访问的查询任务状态和结果。
     *
     * @param queryId 查询任务标识
     * @return 查询任务快照响应
     */
    @GetMapping("/{queryId}")
    public ApiResponse<QueryStateStore.QuerySnapshot> get(@PathVariable String queryId) {
        return ApiResponse.ok(service.get(queryId));
    }

    /**
     * 取消当前用户有权访问且尚未结束的查询任务。
     *
     * @param queryId 查询任务标识
     * @return 空成功响应
     */
    @PostMapping("/{queryId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String queryId) {
        service.cancel(queryId);
        return ApiResponse.ok();
    }

    /**
     * 查询提交结果。
     *
     * @param queryId 新建的查询任务标识
     */
    public record SubmitResult(String queryId) {}
}
