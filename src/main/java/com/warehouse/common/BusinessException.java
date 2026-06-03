package com.warehouse.common;

public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public int getCode() { return code; }
}
