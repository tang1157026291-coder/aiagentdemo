package com.zoujuexian.aiagentdemo.core.agent;

public class AgentMessage {

    private String from;
    private String to;
    private String taskId;
    private String content;
    private String status;
    private long timestamp;

    public AgentMessage() {}

    public AgentMessage(String from, String to, String content) {
        this.from = from;
        this.to = to;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.status = "PENDING";
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
