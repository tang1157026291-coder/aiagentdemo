package com.zoujuexian.aiagentdemo.api.controller.dto;

import java.util.Map;

/**
 * 对话响应
 */
public class ChatResponse {

    private boolean success;
    private String reply;
    private String error;
    private Map<String, String> data;

    public static ChatResponse ok(String reply) {
        ChatResponse response = new ChatResponse();
        response.success = true;
        response.reply = reply;
        return response;
    }

    public static ChatResponse ok(String reply, Map<String, String> data) {
        ChatResponse response = new ChatResponse();
        response.success = true;
        response.reply = reply;
        response.data = data;
        return response;
    }

    public static ChatResponse fail(String error) {
        ChatResponse response = new ChatResponse();
        response.success = false;
        response.error = error;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }
}
