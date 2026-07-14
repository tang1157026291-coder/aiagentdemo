package com.zoujuexian.aiagentdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private String systemPrompt = "你是一个智能助手，具备知识库检索（RAG）、工具调用（Function Calling）、"
            + "技能执行（Skill）、MCP 协议连接和子代理（SubAgent）等能力。\n"
            + "当遇到需要独立上下文记忆的复杂子任务时，你可以创建 SubAgent 来处理。\n"
            + "请根据用户的问题，合理选择使用工具或直接回答。\n"
            + "回答时请简洁准确，必要时引用工具返回的结果。";

    private int maxIterations = 10;
    private double temperature = 0.7;
    private int maxTokens = 2048;
    private double topP = 1.0;

    private Orchestration orchestration = new Orchestration();

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        this.topP = topP;
    }

    public Orchestration getOrchestration() {
        return orchestration;
    }

    public void setOrchestration(Orchestration orchestration) {
        this.orchestration = orchestration;
    }

    public static class Orchestration {
        private boolean enabled = true;
        private String mode = "hierarchical";
        private int maxAgents = 10;
        private int timeoutSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public int getMaxAgents() {
            return maxAgents;
        }

        public void setMaxAgents(int maxAgents) {
            this.maxAgents = maxAgents;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
