package com.zoujuexian.aiagentdemo.core.agent;

import java.util.Map;

public class AgentTask {

    private String id;
    private String name;
    private String description;
    private String input;
    private Map<String, Object> parameters;
    private String assignedAgentId;
    private String status;

    public AgentTask() {}

    public AgentTask(String id, String name, String description, String input) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.input = input;
        this.status = "PENDING";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getAssignedAgentId() {
        return assignedAgentId;
    }

    public void setAssignedAgentId(String assignedAgentId) {
        this.assignedAgentId = assignedAgentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
