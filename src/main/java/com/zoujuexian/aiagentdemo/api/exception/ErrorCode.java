package com.zoujuexian.aiagentdemo.api.exception;

public enum ErrorCode {

    SUCCESS("0", "成功"),

    BAD_REQUEST("400", "请求参数错误"),
    UNAUTHORIZED("401", "未授权"),
    FORBIDDEN("403", "禁止访问"),
    NOT_FOUND("404", "资源不存在"),

    INTERNAL_ERROR("500", "服务器内部错误"),
    SERVICE_UNAVAILABLE("503", "服务暂时不可用"),

    MODEL_ERROR("1001", "模型调用失败"),
    RAG_ERROR("1002", "知识库检索失败"),
    TOOL_ERROR("1003", "工具调用失败"),
    INTENT_ERROR("1004", "意图识别失败"),
    MCP_ERROR("1005", "MCP服务调用失败"),
    WORKFLOW_ERROR("1006", "工作流执行失败"),
    AGENT_ERROR("1007", "Agent执行失败"),

    VALIDATION_ERROR("2001", "数据校验失败"),
    CONFIG_ERROR("2002", "配置错误"),
    QUOTA_EXCEEDED("2003", "配额超限"),
    SESSION_EXPIRED("2004", "会话已过期"),

    GUARDRAIL_BLOCKED("3001", "内容安全检查未通过");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
