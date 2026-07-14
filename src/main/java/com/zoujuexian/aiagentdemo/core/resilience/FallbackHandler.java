package com.zoujuexian.aiagentdemo.core.resilience;

import org.springframework.stereotype.Component;

@Component
public class FallbackHandler {

    public String handleModelError(String fallbackMessage) {
        return "[服务暂时不可用] " + fallbackMessage;
    }

    public String handleToolError(String toolName, String errorMessage) {
        return "[工具调用失败] " + toolName + ": " + errorMessage;
    }

    public String handleRagError(String query, String errorMessage) {
        return "[知识库检索失败] 无法获取相关信息: " + errorMessage;
    }

    public String handleMcpError(String serverUrl, String errorMessage) {
        return "[MCP服务不可用] " + serverUrl + ": " + errorMessage;
    }

    public String handleGenericError(String operation, String errorMessage) {
        return "[操作失败] " + operation + ": " + errorMessage;
    }
}
