package com.zoujuexian.aiagentdemo.core.agent;

import java.util.List;

public class AgentResult {

    private String taskId;
    private String agentId;
    private String agentName;
    private String content;
    private boolean success;
    private String errorMessage;
    private long executionTimeMs;
    private List<String> references;

    public AgentResult() {}

    public AgentResult(String taskId, String agentId, String agentName, String content) {
        this.taskId = taskId;
        this.agentId = agentId;
        this.agentName = agentName;
        this.content = content;
        this.success = true;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.success = false;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public List<String> getReferences() {
        return references;
    }

    public void setReferences(List<String> references) {
        this.references = references;
    }
}
