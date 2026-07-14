package com.zoujuexian.aiagentdemo.api.exception;

public class GuardrailException extends RuntimeException {

    public GuardrailException(String message) {
        super(message);
    }

    public GuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
