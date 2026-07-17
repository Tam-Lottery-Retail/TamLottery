package com.mtriet.tamlottery.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    public BusinessException(ErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    public static BusinessException conflict(ErrorCode code, String message) {
        return new BusinessException(code, HttpStatus.CONFLICT, message);
    }

    public static BusinessException invalid(ErrorCode code, String message) {
        return new BusinessException(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

