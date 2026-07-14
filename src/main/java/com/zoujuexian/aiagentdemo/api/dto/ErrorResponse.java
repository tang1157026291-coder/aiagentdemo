package com.zoujuexian.aiagentdemo.api.dto;

import com.zoujuexian.aiagentdemo.api.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;

public class ErrorResponse {

    private String code;
    private String message;
    private String detail;
    private LocalDateTime timestamp;
    private String path;
    private List<String> errors;

    public static ErrorResponse of(ErrorCode errorCode) {
        ErrorResponse response = new ErrorResponse();
        response.code = errorCode.getCode();
        response.message = errorCode.getMessage();
        response.timestamp = LocalDateTime.now();
        return response;
    }

    public static ErrorResponse of(ErrorCode errorCode, String detail) {
        ErrorResponse response = of(errorCode);
        response.detail = detail;
        return response;
    }

    public static ErrorResponse of(ErrorCode errorCode, String detail, String path) {
        ErrorResponse response = of(errorCode, detail);
        response.path = path;
        return response;
    }

    public static ErrorResponse of(ErrorCode errorCode, List<String> errors) {
        ErrorResponse response = of(errorCode);
        response.errors = errors;
        return response;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
