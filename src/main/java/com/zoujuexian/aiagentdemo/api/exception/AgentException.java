package com.zoujuexian.aiagentdemo.api.exception;

public class AgentException extends RuntimeException {

    private final ErrorCode errorCode;

    public AgentException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AgentException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AgentException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
