package com.omni.panel.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将业务、校验、鉴权及未预期异常转换为统一的 {@link ApiResponse}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 将业务异常中的状态码与提示信息原样转换为失败响应。
     *
     * @param exception 业务异常
     * @return 业务失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException exception) {
        return ApiResponse.error(exception.code(), exception.getMessage());
    }

    /**
     * 将请求参数校验异常转换为统一的参数错误响应。
     *
     * @param exception 参数校验异常
     * @return 参数错误响应
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ApiResponse<Void> handleValidation(Exception exception) {
        return ApiResponse.error(400, "请求参数不合法");
    }

    /**
     * 将访问拒绝异常转换为 HTTP 403 响应。
     *
     * @return 无权访问响应
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleForbidden() {
        return ApiResponse.error(403, "无权访问该资源");
    }

    /**
     * 记录未预期异常并向调用方隐藏内部错误细节。
     *
     * @param exception 未预期异常
     * @return 服务内部错误响应
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknown(Exception exception) {
        log.error("请求处理失败", exception);
        return ApiResponse.error(500, "服务内部错误");
    }
}
