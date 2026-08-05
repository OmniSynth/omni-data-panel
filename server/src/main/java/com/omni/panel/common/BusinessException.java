package com.omni.panel.common;

/**
 * 表示可向 API 调用方返回明确状态码与提示信息的业务异常。
 */
public class BusinessException extends RuntimeException {
    private final int code;

    /**
     * 构造默认 400 状态码的业务异常。
     *
     * @param message 错误提示信息
     */
    public BusinessException(String message) {
        this(400, message);
    }

    /**
     * 构造指定业务状态码的异常。
     *
     * @param code    业务状态码
     * @param message 错误提示信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 返回业务状态码。
     *
     * @return 业务状态码
     */
    public int code() {
        return code;
    }
}
