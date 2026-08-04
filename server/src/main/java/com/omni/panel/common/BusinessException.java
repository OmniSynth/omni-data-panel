package com.omni.panel.common;

/**
 * 表示可向 API 调用方返回明确状态码与提示信息的业务异常。
 */
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
