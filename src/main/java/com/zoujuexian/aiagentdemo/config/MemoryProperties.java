package com.zoujuexian.aiagentdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    private int maxRounds = 20;
    private int maxAssistantMessages = 3;
    private int compressionThreshold = 15;
    private int expireMinutes = 1440;
    private int subAgentMaxRounds = 10;
    private int subAgentMaxAssistantMessages = 3;

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public int getMaxAssistantMessages() {
        return maxAssistantMessages;
    }

    public void setMaxAssistantMessages(int maxAssistantMessages) {
        this.maxAssistantMessages = maxAssistantMessages;
    }

    public int getCompressionThreshold() {
        return compressionThreshold;
    }

    public void setCompressionThreshold(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    public int getExpireMinutes() {
        return expireMinutes;
    }

    public void setExpireMinutes(int expireMinutes) {
        this.expireMinutes = expireMinutes;
    }

    public int getSubAgentMaxRounds() {
        return subAgentMaxRounds;
    }

    public void setSubAgentMaxRounds(int subAgentMaxRounds) {
        this.subAgentMaxRounds = subAgentMaxRounds;
    }

    public int getSubAgentMaxAssistantMessages() {
        return subAgentMaxAssistantMessages;
    }

    public void setSubAgentMaxAssistantMessages(int subAgentMaxAssistantMessages) {
        this.subAgentMaxAssistantMessages = subAgentMaxAssistantMessages;
    }
}
