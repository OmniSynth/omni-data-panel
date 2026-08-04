package com.omni.panel.common;

/**
 * 统一 API 响应，封装业务状态码、提示信息与响应数据。
 *
 * @param code 业务状态码，{@code 0} 表示成功
 * @param message 响应提示信息
 * @param data 响应数据
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(int code, String message, T data) {
    /**
     * 创建携带数据的成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "成功", data);
    }

    /**
     * 创建无响应数据的成功响应。
     *
     * @return 成功响应
     */
    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    /**
     * 创建业务失败响应。
     *
     * @param code 业务状态码
     * @param message 错误提示信息
     * @return 失败响应
     */
    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
