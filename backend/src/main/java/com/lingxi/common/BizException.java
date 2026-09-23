package com.lingxi.common;

public class BizException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public BizException(String message) {
        this(400, message, 400);
    }

    public BizException(int code, String message) {
        this(code, message, 400);
    }

    public BizException(int code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
